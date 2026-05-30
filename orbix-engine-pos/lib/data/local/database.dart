import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

part 'database.g.dart';

/// Local SQLite schema for the POS device.
/// Mirrors the offline-first design in ARCHITECTURE.md §3.1.
///
/// Schema history:
///   v1 — initial schema (Items, Barcodes, TillSessions, PosSales, PosSaleLines, Outbox)
///   v2 — offline-sync spine (US-POS-017/018):
///          Outbox: add seq, dependsOn, serverEntityId, serverNumber; extend status enum
///          PosSales: add serverNumber, serverEntityId, serverEntityUid
///          TillSessions: add clientOpId (the TILL_SESSION_OPEN outbox id)
///          New: SyncCursors (stores opaque pull cursor per dataset)
///          New: Customers (populated by pull/bootstrap)
///          New: PriceRows (populated by pull/bootstrap)

// ---------------------------------------------------------------------------
// Catalog / reference tables (pulled from server)
// ---------------------------------------------------------------------------

class Items extends Table {
  IntColumn get id => integer()();
  TextColumn get uid => text().nullable()();
  TextColumn get code => text()();
  TextColumn get name => text()();
  TextColumn get shortName => text().nullable()();
  RealColumn get price => real()();
  TextColumn get vatGroup => text()();
  IntColumn get itemGroupId => integer()();
  BoolColumn get isActive => boolean().withDefault(const Constant(true))();
  @override
  Set<Column> get primaryKey => {id};
}

class Barcodes extends Table {
  TextColumn get barcode => text()();
  IntColumn get itemId => integer()();
  RealColumn get packQty => real().withDefault(const Constant(1))();
  @override
  Set<Column> get primaryKey => {barcode};
}

/// Customer rows populated by pull/bootstrap (catalog dataset).
class Customers extends Table {
  IntColumn get id => integer()();
  TextColumn get uid => text().nullable()();
  TextColumn get code => text()();
  TextColumn get name => text()();
  BoolColumn get isWalkIn => boolean().withDefault(const Constant(false))();
  BoolColumn get isActive => boolean().withDefault(const Constant(true))();
  @override
  Set<Column> get primaryKey => {id};
}

/// Price rows populated by pull/bootstrap (price dataset).
class PriceRows extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get itemId => integer()();
  TextColumn get priceListCode => text()();
  RealColumn get price => real()();
  TextColumn get currency => text().withDefault(const Constant('TZS'))();
}

// ---------------------------------------------------------------------------
// Till session
// ---------------------------------------------------------------------------

class TillSessions extends Table {
  IntColumn get id => integer().autoIncrement()();
  /// The clientOpId of the TILL_SESSION_OPEN outbox op — used by till-close reconciliation.
  TextColumn get clientOpId => text().nullable()();
  /// Server uid once the TILL_SESSION_OPEN op is ACCEPTED.
  TextColumn get serverUid => text().nullable()();
  IntColumn get tillId => integer()();
  TextColumn get businessDate => text()();
  IntColumn get openedBy => integer()();
  DateTimeColumn get openedAt => dateTime()();
  RealColumn get openingFloat => real()();
  DateTimeColumn get closedAt => dateTime().nullable()();
  RealColumn get declaredCash => real().nullable()();
  TextColumn get status => text()(); // OPEN | CLOSED | RECONCILED
}

// ---------------------------------------------------------------------------
// Sales
// ---------------------------------------------------------------------------

class PosSales extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get clientOpId => text()();
  IntColumn get tillSessionId => integer()();
  IntColumn get customerId => integer()();
  RealColumn get total => real()();
  DateTimeColumn get saleAt => dateTime()();
  TextColumn get status => text()(); // POSTED | VOIDED
  BoolColumn get synced => boolean().withDefault(const Constant(false))();
  /// Server-assigned document number (e.g. POS-1-20260530-00042). Null until synced.
  TextColumn get serverNumber => text().nullable()();
  /// Server row id (string per JSON:API). Null until synced.
  TextColumn get serverEntityId => text().nullable()();
  /// Server uid for navigation. Null until synced.
  TextColumn get serverEntityUid => text().nullable()();
}

class PosSaleLines extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get saleId => integer()();
  IntColumn get itemId => integer()();
  RealColumn get qty => real()();
  RealColumn get unitPrice => real()();
  RealColumn get lineTotal => real()();
}

// ---------------------------------------------------------------------------
// Device outbox (transport queue for offline ops)
// Status values: PENDING | INFLIGHT | CONFIRMED | REJECTED | DEFERRED | NEEDS_REVIEW
// ---------------------------------------------------------------------------

class Outbox extends Table {
  /// Crockford ULID, client-generated, canonical idempotency key.
  TextColumn get clientOpId => text()();
  TextColumn get opType => text()();
  /// Monotonic per-device sequence counter (advisory; see slice-sync-spine.md §2.5).
  IntColumn get seq => integer().withDefault(const Constant(0))();
  /// clientOpId of the op that must be ACCEPTED/DUPLICATE before this one; null for no dep.
  TextColumn get dependsOn => text().nullable()();
  TextColumn get payloadJson => text()();
  DateTimeColumn get occurredAt => dateTime()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get lastAttemptAt => dateTime().nullable()();
  IntColumn get attemptCount => integer().withDefault(const Constant(0))();
  /// PENDING | INFLIGHT | CONFIRMED | REJECTED | DEFERRED | NEEDS_REVIEW
  TextColumn get status => text().withDefault(const Constant('PENDING'))();
  /// Server row id returned on ACCEPTED/DUPLICATE. Null until confirmed.
  TextColumn get serverEntityId => text().nullable()();
  /// Server document number returned on ACCEPTED/DUPLICATE. Null until confirmed.
  TextColumn get serverNumber => text().nullable()();
  @override
  Set<Column> get primaryKey => {clientOpId};
}

// ---------------------------------------------------------------------------
// Sync cursor — stores the opaque pull-cursor token per dataset.
// In v1 there is one row (dataset = 'global').
// ---------------------------------------------------------------------------

class SyncCursors extends Table {
  /// Dataset name: 'global' in v1; later per-dataset names.
  TextColumn get dataset => text()();
  /// Opaque base64 token from the server; replay verbatim on next pull.
  TextColumn get token => text()();
  DateTimeColumn get updatedAt => dateTime()();
  @override
  Set<Column> get primaryKey => {dataset};
}

// ---------------------------------------------------------------------------
// Database class
// ---------------------------------------------------------------------------

@DriftDatabase(tables: [
  Items, Barcodes,
  Customers, PriceRows,
  TillSessions, PosSales, PosSaleLines,
  Outbox, SyncCursors,
])
class PosDatabase extends _$PosDatabase {
  PosDatabase() : super(_open());
  PosDatabase.forTesting(super.executor);

  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) => m.createAll(),
    onUpgrade: (m, from, to) async {
      if (from < 2) {
        // v1 → v2: add new columns to existing tables + new tables.
        // New tables
        await m.createTable(customers);
        await m.createTable(priceRows);
        await m.createTable(syncCursors);

        // Outbox: new columns
        await m.addColumn(outbox, outbox.seq);
        await m.addColumn(outbox, outbox.dependsOn);
        await m.addColumn(outbox, outbox.occurredAt);
        await m.addColumn(outbox, outbox.serverEntityId);
        await m.addColumn(outbox, outbox.serverNumber);

        // PosSales: new columns
        await m.addColumn(posSales, posSales.serverNumber);
        await m.addColumn(posSales, posSales.serverEntityId);
        await m.addColumn(posSales, posSales.serverEntityUid);

        // TillSessions: new columns
        await m.addColumn(tillSessions, tillSessions.clientOpId);
        await m.addColumn(tillSessions, tillSessions.serverUid);

        // Items: add uid column
        await m.addColumn(items, items.uid);

        // Seed occurredAt for existing outbox rows so the NOT NULL won't choke
        await customStatement(
          'UPDATE outbox SET occurred_at = created_at WHERE occurred_at IS NULL',
        );
      }
    },
  );
}

LazyDatabase _open() {
  return LazyDatabase(() async {
    final dir = await getApplicationSupportDirectory();
    final file = File(p.join(dir.path, 'orbix-pos.db'));
    return NativeDatabase.createInBackground(file);
  });
}
