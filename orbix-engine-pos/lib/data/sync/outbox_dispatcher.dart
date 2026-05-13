import 'dart:async';

import 'package:logger/logger.dart';

/// Polls the local outbox and pushes ops to /api/v1/sync/push.
/// Idempotent via client_op_id (see ARCHITECTURE.md §2.9 / §5.2).
class OutboxDispatcher {
  OutboxDispatcher({Duration? interval, Logger? logger})
      : _interval = interval ?? const Duration(seconds: 5),
        _log = logger ?? Logger();

  final Duration _interval;
  final Logger _log;
  Timer? _timer;

  void start() {
    _timer ??= Timer.periodic(_interval, (_) => _flush());
    _log.i('OutboxDispatcher started; interval=${_interval.inSeconds}s');
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> _flush() async {
    // TODO: read PENDING from Outbox table, POST to /sync/push,
    // mark SENT on success, increment attempt + record FAILED on error.
  }
}
