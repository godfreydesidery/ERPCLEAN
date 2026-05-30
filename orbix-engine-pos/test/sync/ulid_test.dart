// Tests for the Crockford ULID generator.
// Coverage: format validation, monotonic ordering, uniqueness.

import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/core/ulid.dart';

void main() {
  group('generateUlid', () {
    test('returns 26-character string', () {
      final id = generateUlid();
      expect(id.length, 26);
    });

    test('uses Crockford base32 alphabet only', () {
      const alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
      for (var i = 0; i < 100; i++) {
        final id = generateUlid();
        for (final ch in id.split('')) {
          expect(alphabet.contains(ch), isTrue, reason: 'unexpected char $ch in ULID $id');
        }
      }
    });

    test('two consecutive ULIDs are unique', () {
      final ids = {for (var i = 0; i < 1000; i++) generateUlid()};
      expect(ids.length, 1000);
    });

    test('ULIDs generated at different times are lexically non-decreasing', () {
      // Generate one, pause 2ms, generate another.
      // The timestamp prefix must be >= because time advanced.
      final id1 = generateUlid();
      // Busy-wait 2ms so the millisecond counter advances.
      final deadline = DateTime.now().millisecondsSinceEpoch + 2;
      while (DateTime.now().millisecondsSinceEpoch < deadline) {}
      final id2 = generateUlid();

      // The first 10 chars encode the timestamp.
      final ts1 = id1.substring(0, 10);
      final ts2 = id2.substring(0, 10);
      expect(ts2.compareTo(ts1), greaterThanOrEqualTo(0),
          reason: 'id2 timestamp must be >= id1 timestamp when generated later');
    });
  });
}
