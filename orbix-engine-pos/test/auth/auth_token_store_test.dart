// Tests for AuthTokenStore — persist, round-trip, clear.
// Uses SharedPreferences.setMockInitialValues for an in-memory store.
//
// Coverage:
// - save() persists all fields
// - read() round-trips all fields back
// - accessTokenValid returns true for a non-expired token
// - accessTokenValid returns false for an expired/invalid token
// - updateTokens() replaces only access + refresh tokens
// - clear() removes all keys; read() returns null afterwards
// - hasAnySession / hasValidSession gates

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:orbix_engine_pos/data/auth/auth_token_store.dart';

/// A minimal valid JWT with exp far in the future (year 2099).
/// Header: {"alg":"HS256","typ":"JWT"}
/// Payload: {"sub":"1","exp":4102444800}
const _futureToken =
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9'
    '.eyJzdWIiOiIxIiwiZXhwIjo0MTAyNDQ0ODAwfQ'
    '.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c';

/// JWT with exp in the past (year 2000).
const _expiredToken =
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9'
    '.eyJzdWIiOiIxIiwiZXhwIjo5NDY2ODQ4MDB9'
    '.placeholder';


void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('AuthTokenStore', () {
    // -------------------------------------------------------------------------
    // persist / round-trip
    // -------------------------------------------------------------------------

    test('save() and read() round-trip all fields', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: _futureToken,
        refreshToken: 'refresh-abc',
        userId: 42,
        username: 'cashier1',
        displayName: 'Cashier One',
        defaultCompanyId: 1,
        defaultBranchId: 7,
      );

      final session = store.read();
      expect(session, isNotNull);
      expect(session!.accessToken, _futureToken);
      expect(session.refreshToken, 'refresh-abc');
      expect(session.userId, 42);
      expect(session.username, 'cashier1');
      expect(session.displayName, 'Cashier One');
      expect(session.defaultCompanyId, 1);
      expect(session.defaultBranchId, 7);
    });

    test('effectiveBranchId falls back to defaultBranchId when no activeBranchId', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: _futureToken,
        refreshToken: 'r',
        userId: 1,
        username: 'u',
        displayName: 'd',
        defaultBranchId: 5,
      );

      // On first save, activeBranchId is seeded from defaultBranchId.
      final session = store.read()!;
      expect(session.effectiveBranchId, 5);
    });

    // -------------------------------------------------------------------------
    // accessTokenValid
    // -------------------------------------------------------------------------

    test('accessTokenValid returns true for a non-expired token', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: _futureToken,
        refreshToken: 'r',
        userId: 1,
        username: 'u',
        displayName: 'd',
      );

      expect(store.read()!.accessTokenValid, isTrue);
    });

    test('accessTokenValid returns false for an expired token', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: _expiredToken,
        refreshToken: 'r',
        userId: 1,
        username: 'u',
        displayName: 'd',
      );

      expect(store.read()!.accessTokenValid, isFalse);
    });

    test('accessTokenValid returns false for a malformed token', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: 'not.a.jwt',
        refreshToken: 'r',
        userId: 1,
        username: 'u',
        displayName: 'd',
      );

      expect(store.read()!.accessTokenValid, isFalse);
    });

    // -------------------------------------------------------------------------
    // updateTokens
    // -------------------------------------------------------------------------

    test('updateTokens replaces only access + refresh tokens', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: _expiredToken,
        refreshToken: 'old-refresh',
        userId: 99,
        username: 'mgr',
        displayName: 'Manager',
      );

      await store.updateTokens(
        accessToken: _futureToken,
        refreshToken: 'new-refresh',
      );

      final session = store.read()!;
      expect(session.accessToken, _futureToken);
      expect(session.refreshToken, 'new-refresh');
      // Other fields unchanged
      expect(session.userId, 99);
      expect(session.username, 'mgr');
    });

    // -------------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------------

    test('clear() removes all keys; read() returns null', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: _futureToken,
        refreshToken: 'r',
        userId: 1,
        username: 'u',
        displayName: 'd',
      );
      expect(store.read(), isNotNull);

      await store.clear();

      expect(store.read(), isNull);
      expect(store.accessToken, isNull);
      expect(store.refreshToken, isNull);
      expect(store.hasAnySession, isFalse);
    });

    // -------------------------------------------------------------------------
    // hasAnySession / hasValidSession
    // -------------------------------------------------------------------------

    test('hasAnySession is false before any save', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);
      expect(store.hasAnySession, isFalse);
    });

    test('hasAnySession is true even for an expired token', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: _expiredToken,
        refreshToken: 'r',
        userId: 1,
        username: 'u',
        displayName: 'd',
      );

      expect(store.hasAnySession, isTrue);
      expect(store.hasValidSession, isFalse);
    });

    test('hasValidSession is true for a future token', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = AuthTokenStore(prefs);

      await store.save(
        accessToken: _futureToken,
        refreshToken: 'r',
        userId: 1,
        username: 'u',
        displayName: 'd',
      );

      expect(store.hasValidSession, isTrue);
    });
  });
}
