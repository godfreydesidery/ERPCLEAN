/// Riverpod providers for the local catalog (Drift-backed).
///
/// [catalogItemsProvider] — StreamProvider<List<CatalogItem>>
///   The product grid reads this.  Emits an empty list until items are synced.
///
/// [catalogSyncStateProvider] — StateProvider<CatalogSyncState>
///   Tracks whether a catalog pull is currently in flight and whether it
///   finished with an error.  The UI shows a loading banner / error banner
///   based on this.
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/core_providers.dart';
import '../../data/sync/sync_providers.dart';
import 'catalog_item.dart';
import 'catalog_repository.dart';

// ---------------------------------------------------------------------------
// Catalog repository
// ---------------------------------------------------------------------------

final catalogRepositoryProvider = Provider<CatalogRepository>((ref) {
  return CatalogRepository(
    db: ref.watch(posDatabaseProvider),
    logger: ref.watch(loggerProvider),
  );
});

// ---------------------------------------------------------------------------
// Live stream — product grid binds to this
// ---------------------------------------------------------------------------

/// Streams the active catalog joined to prices from local Drift tables.
/// No network involved; data is populated by the sync pull.
final catalogItemsProvider = StreamProvider<List<CatalogItem>>((ref) {
  return ref.watch(catalogRepositoryProvider).watchCatalog();
});

// ---------------------------------------------------------------------------
// Sync state — UI loading / error indicators
// ---------------------------------------------------------------------------

enum CatalogSyncPhase { idle, loading, error }

class CatalogSyncState {
  const CatalogSyncState({
    this.phase = CatalogSyncPhase.idle,
    this.errorMessage,
  });

  final CatalogSyncPhase phase;
  final String? errorMessage;

  bool get isLoading => phase == CatalogSyncPhase.loading;
  bool get hasError => phase == CatalogSyncPhase.error;

  CatalogSyncState copyWith({CatalogSyncPhase? phase, String? errorMessage}) =>
      CatalogSyncState(
        phase: phase ?? this.phase,
        errorMessage: errorMessage,
      );
}

final catalogSyncStateProvider =
    StateProvider<CatalogSyncState>((_) => const CatalogSyncState());

// ---------------------------------------------------------------------------
// Catalog sync action — called from UI widgets (login, cart screen).
//
// Accepts [WidgetRef] because callers are always in a ConsumerWidget or
// ConsumerStatefulWidget.  Fires in the background; does not block the frame.
// ---------------------------------------------------------------------------

/// Triggers a catalog pull (bootstrap on first run) and updates
/// [catalogSyncStateProvider].  Fire-and-forget: call without await.
Future<void> triggerCatalogSync(WidgetRef ref) async {
  final notifier = ref.read(catalogSyncStateProvider.notifier);
  if (ref.read(catalogSyncStateProvider).isLoading) return; // already running

  notifier.state = const CatalogSyncState(phase: CatalogSyncPhase.loading);

  final log = ref.read(loggerProvider);
  final syncRepo = ref.read(syncRepositoryProvider);

  try {
    await syncRepo.pull();
    notifier.state = const CatalogSyncState(phase: CatalogSyncPhase.idle);
    log.i('triggerCatalogSync: pull complete');
  } catch (e, st) {
    log.e('triggerCatalogSync: pull failed', error: e, stackTrace: st);
    notifier.state = CatalogSyncState(
      phase: CatalogSyncPhase.error,
      errorMessage: e.toString(),
    );
  }
}
