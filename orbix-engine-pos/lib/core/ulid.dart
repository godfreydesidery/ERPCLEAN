/// Crockford ULID generator (26-char, base32 encoded).
/// Matches the server-side identity discipline: lexically sortable,
/// globally unique, 128-bit (48-bit ms timestamp + 80-bit random).
///
/// Design ref: slice-sync-spine.md §2.2 "clientOpId is a Crockford ULID".
library;

import 'dart:math';

/// Crockford base-32 alphabet (uppercase, no I/L/O/U).
const _alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

final _random = Random.secure();

/// Generates a new Crockford ULID string (26 characters).
String generateUlid() {
  final now = DateTime.now().millisecondsSinceEpoch;

  // 10 chars for 48-bit timestamp
  final timePart = StringBuffer();
  var t = now;
  for (var i = 0; i < 10; i++) {
    timePart.write(_alphabet[t % 32]);
    t ~/= 32;
  }
  final timeStr = timePart.toString().split('').reversed.join();

  // 16 chars for 80-bit random
  final randPart = StringBuffer();
  for (var i = 0; i < 16; i++) {
    randPart.write(_alphabet[_random.nextInt(32)]);
  }

  return timeStr + randPart.toString();
}
