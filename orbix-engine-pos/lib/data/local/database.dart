import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

part 'database.g.dart';

/// Local SQLite schema for the POS device.
/// Mirrors the offline-first design in ARCHITECTURE.md §3.1.

class Items extends Table {
  IntColumn get id => integer()();
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

class TillSessions extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get tillId => integer()();
  TextColumn get businessDate => text()();
  IntColumn get openedBy => integer()();
  DateTimeColumn get openedAt => dateTime()();
  RealColumn get openingFloat => real()();
  DateTimeColumn get closedAt => dateTime().nullable()();
  RealColumn get declaredCash => real().nullable()();
  TextColumn get status => text()(); // OPEN | CLOSED | RECONCILED
}

class PosSales extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get clientOpId => text()();
  IntColumn get tillSessionId => integer()();
  IntColumn get customerId => integer()();
  RealColumn get total => real()();
  DateTimeColumn get saleAt => dateTime()();
  TextColumn get status => text()(); // POSTED | VOIDED
  BoolColumn get synced => boolean().withDefault(const Constant(false))();
}

class PosSaleLines extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get saleId => integer()();
  IntColumn get itemId => integer()();
  RealColumn get qty => real()();
  RealColumn get unitPrice => real()();
  RealColumn get lineTotal => real()();
}

class Outbox extends Table {
  TextColumn get clientOpId => text()();
  TextColumn get opType => text()();
  TextColumn get payloadJson => text()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get lastAttemptAt => dateTime().nullable()();
  IntColumn get attemptCount => integer().withDefault(const Constant(0))();
  TextColumn get status => text()(); // PENDING | SENT | FAILED
  @override
  Set<Column> get primaryKey => {clientOpId};
}

@DriftDatabase(tables: [Items, Barcodes, TillSessions, PosSales, PosSaleLines, Outbox])
class PosDatabase extends _$PosDatabase {
  PosDatabase() : super(_open());

  @override
  int get schemaVersion => 1;
}

LazyDatabase _open() {
  return LazyDatabase(() async {
    final dir = await getApplicationSupportDirectory();
    final file = File(p.join(dir.path, 'orbix-pos.db'));
    return NativeDatabase.createInBackground(file);
  });
}
