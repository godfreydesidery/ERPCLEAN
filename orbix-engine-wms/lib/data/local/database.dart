import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

part 'database.g.dart';

/// Local SQLite schema for the WMS device.
/// Mirrors ARCHITECTURE.md §3.2.

class Items extends Table {
  IntColumn get id => integer()();
  TextColumn get code => text()();
  TextColumn get name => text()();
  RealColumn get price => real()();
  @override
  Set<Column> get primaryKey => {id};
}

class Customers extends Table {
  IntColumn get id => integer()();
  TextColumn get code => text()();
  TextColumn get name => text()();
  RealColumn get currentDebt => real().withDefault(const Constant(0))();
  RealColumn get creditLimit => real().withDefault(const Constant(0))();
  @override
  Set<Column> get primaryKey => {id};
}

class VanStock extends Table {
  IntColumn get itemId => integer()();
  RealColumn get qtyLoaded => real()();
  RealColumn get qtySold => real().withDefault(const Constant(0))();
  @override
  Set<Column> get primaryKey => {itemId};
}

class SheetSales extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get clientOpId => text()();
  IntColumn get customerId => integer()();
  DateTimeColumn get soldAt => dateTime()();
  TextColumn get paymentTerms => text()(); // CASH | CREDIT
  RealColumn get total => real()();
  RealColumn get paid => real()();
  RealColumn get gpsLat => real().nullable()();
  RealColumn get gpsLng => real().nullable()();
  BoolColumn get synced => boolean().withDefault(const Constant(false))();
}

class SheetSaleLines extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get saleId => integer()();
  IntColumn get itemId => integer()();
  RealColumn get qty => real()();
  RealColumn get unitPrice => real()();
  RealColumn get lineTotal => real()();
}

class Expenses extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get clientOpId => text()();
  TextColumn get category => text()();
  RealColumn get amount => real()();
  DateTimeColumn get at => dateTime()();
  TextColumn get description => text().nullable()();
}

class Outbox extends Table {
  TextColumn get clientOpId => text()();
  TextColumn get opType => text()();
  TextColumn get payloadJson => text()();
  DateTimeColumn get createdAt => dateTime()();
  IntColumn get attemptCount => integer().withDefault(const Constant(0))();
  TextColumn get status => text()();
  @override
  Set<Column> get primaryKey => {clientOpId};
}

@DriftDatabase(tables: [Items, Customers, VanStock, SheetSales, SheetSaleLines, Expenses, Outbox])
class WmsDatabase extends _$WmsDatabase {
  WmsDatabase() : super(_open());

  @override
  int get schemaVersion => 1;
}

LazyDatabase _open() {
  return LazyDatabase(() async {
    final dir = await getApplicationSupportDirectory();
    final file = File(p.join(dir.path, 'orbix-wms.db'));
    return NativeDatabase.createInBackground(file);
  });
}
