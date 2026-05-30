/// Riverpod providers for the cash-movement feature (US-POS-013 / US-POS-014).
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/core_providers.dart';
import '../../data/sync/sync_providers.dart';
import 'cash_movement_repository.dart';

final cashMovementRepositoryProvider = Provider<CashMovementRepository>((ref) {
  return CashMovementRepository(
    db: ref.watch(posDatabaseProvider),
    outbox: ref.watch(outboxRepositoryProvider),
    logger: ref.watch(loggerProvider),
  );
});
