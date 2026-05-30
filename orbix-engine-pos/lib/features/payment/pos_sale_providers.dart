/// Riverpod providers for the POS sale feature.
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/core_providers.dart';
import '../../data/sync/sync_providers.dart';
import 'pos_sale_repository.dart';

final posSaleRepositoryProvider = Provider<PosSaleRepository>((ref) {
  return PosSaleRepository(
    db: ref.watch(posDatabaseProvider),
    outbox: ref.watch(outboxRepositoryProvider),
    logger: ref.watch(loggerProvider),
  );
});
