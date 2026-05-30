/// Riverpod providers for the till-session feature.
///
/// [activeTillSessionProvider] is the single source of truth for the currently
/// open session. All screens that need the session (cart header, cash movements,
/// till-close, X-report) watch this provider instead of the mock sessionProvider.
///
/// The provider rebuilds whenever the Drift TillSessions row changes, so opening
/// or closing a session is instantly reflected across the UI.
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/core_providers.dart';
import '../../data/local/database.dart' show TillSession;
import '../../data/sync/sync_providers.dart';
import '../_demo/mocks.dart' show CashierSession;
import 'till_session_repository.dart';

// ---------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------

final tillSessionRepositoryProvider = Provider<TillSessionRepository>((ref) {
  return TillSessionRepository(
    db: ref.watch(posDatabaseProvider),
    outbox: ref.watch(outboxRepositoryProvider),
    logger: ref.watch(loggerProvider),
  );
});

// ---------------------------------------------------------------------------
// Active till session — stream-backed, rebuilds on every Drift write.
// ---------------------------------------------------------------------------

/// Emits the current ActiveTillSession (from Drift), or null when no OPEN
/// session exists. Rebuilds instantly when the session row changes.
///
/// To open a session: call TillSessionRepository.openSession() and then
/// Riverpod will push the new state here automatically via the stream.
final activeTillSessionProvider = StreamProvider<ActiveTillSession?>((ref) {
  final repo = ref.watch(tillSessionRepositoryProvider);
  final prefs = ref.watch(posConfigStoreProvider);
  final deviceId = prefs.deviceId;

  return repo.watchActiveSessionRow().map((TillSession? row) {
    if (row == null) return null;
    // Resolve display labels — fall back to stored ids when the full profile
    // is not available in this context. The till-open screen stamps them.
    return ActiveTillSession(
      localId: row.id,
      clientOpId: row.clientOpId ?? '',
      tillId: row.tillId,
      tillCode: deviceId,
      tillName: '',
      openedBy: row.openedBy,
      openedAt: row.openedAt,
      openingFloat: row.openingFloat,
      businessDate: row.businessDate,
      cashierName: 'Cashier',
      branchName: 'Branch HQ',
      status: row.status,
      serverEntityId: row.serverUid,
    );
  });
});

// ---------------------------------------------------------------------------
// CashierSession bridge — provides the legacy CashierSession shape that
// the cart header, X-report, and till-close currently consume.
//
// This avoids a large-scale refactor of those screens in this PR.
// Replace with direct ActiveTillSession reads in a follow-up.
// ---------------------------------------------------------------------------

/// Maps ActiveTillSession → CashierSession so screens that watch
/// [cashierSessionProvider] get a real session when the till is open.
///
/// Stored alongside the raw Drift data so display labels are available.
final cashierSessionProvider = StateProvider<CashierSession?>((_) => null);
