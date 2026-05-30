/// Persists and retrieves JWT tokens + decoded identity for the POS session.
///
/// Storage: SharedPreferences (Windows desktop — no system keychain required;
/// acceptable for a locked-down till that itself is the security boundary).
/// Keys are namespaced under "orbix." to match the Angular web client conventions.
///
/// Tokens are kept as raw strings.  The access token is decoded on read via
/// JwtDecoder so callers can check expiry without an extra round-trip.
library;

import 'package:jwt_decoder/jwt_decoder.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _kAccessToken = 'orbix.access';
const _kRefreshToken = 'orbix.refresh';
const _kUserId = 'orbix.user.id';
const _kUsername = 'orbix.user.username';
const _kDisplayName = 'orbix.user.displayName';
const _kDefaultCompanyId = 'orbix.user.defaultCompanyId';
const _kDefaultBranchId = 'orbix.user.defaultBranchId';
const _kActiveBranchId = 'orbix.activeBranchId';

/// Immutable snapshot of the stored session.
class StoredSession {
  const StoredSession({
    required this.accessToken,
    required this.refreshToken,
    required this.userId,
    required this.username,
    required this.displayName,
    this.defaultCompanyId,
    this.defaultBranchId,
    this.activeBranchId,
  });

  final String accessToken;
  final String refreshToken;
  final int userId;
  final String username;
  final String displayName;
  final int? defaultCompanyId;
  final int? defaultBranchId;
  /// Branch override stamped on every request header (X-Branch-Id).
  /// Falls back to defaultBranchId when null.
  final int? activeBranchId;

  /// The branch id to send on outgoing requests.
  int? get effectiveBranchId => activeBranchId ?? defaultBranchId;

  /// True when the access token is not yet expired according to its `exp` claim.
  bool get accessTokenValid {
    try {
      return !JwtDecoder.isExpired(accessToken);
    } catch (_) {
      return false;
    }
  }
}

class AuthTokenStore {
  AuthTokenStore(this._prefs);

  final SharedPreferences _prefs;

  // ---------------------------------------------------------------------------
  // Persist
  // ---------------------------------------------------------------------------

  Future<void> save({
    required String accessToken,
    required String refreshToken,
    required int userId,
    required String username,
    required String displayName,
    int? defaultCompanyId,
    int? defaultBranchId,
  }) async {
    await _prefs.setString(_kAccessToken, accessToken);
    await _prefs.setString(_kRefreshToken, refreshToken);
    await _prefs.setInt(_kUserId, userId);
    await _prefs.setString(_kUsername, username);
    await _prefs.setString(_kDisplayName, displayName);
    if (defaultCompanyId != null) {
      await _prefs.setInt(_kDefaultCompanyId, defaultCompanyId);
    } else {
      await _prefs.remove(_kDefaultCompanyId);
    }
    if (defaultBranchId != null) {
      await _prefs.setInt(_kDefaultBranchId, defaultBranchId);
      // On first login, seed activeBranchId from the JWT default.
      if (!_prefs.containsKey(_kActiveBranchId)) {
        await _prefs.setInt(_kActiveBranchId, defaultBranchId);
      }
    } else {
      await _prefs.remove(_kDefaultBranchId);
    }
  }

  /// Replace tokens only (called by the refresh interceptor).
  Future<void> updateTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _prefs.setString(_kAccessToken, accessToken);
    await _prefs.setString(_kRefreshToken, refreshToken);
  }

  Future<void> clear() async {
    await _prefs.remove(_kAccessToken);
    await _prefs.remove(_kRefreshToken);
    await _prefs.remove(_kUserId);
    await _prefs.remove(_kUsername);
    await _prefs.remove(_kDisplayName);
    await _prefs.remove(_kDefaultCompanyId);
    await _prefs.remove(_kDefaultBranchId);
    await _prefs.remove(_kActiveBranchId);
  }

  // ---------------------------------------------------------------------------
  // Read
  // ---------------------------------------------------------------------------

  /// Returns the stored session, or null if not logged in.
  StoredSession? read() {
    final access = _prefs.getString(_kAccessToken);
    final refresh = _prefs.getString(_kRefreshToken);
    final userId = _prefs.getInt(_kUserId);
    final username = _prefs.getString(_kUsername);
    final displayName = _prefs.getString(_kDisplayName);

    if (access == null || refresh == null || userId == null ||
        username == null || displayName == null) {
      return null;
    }

    return StoredSession(
      accessToken: access,
      refreshToken: refresh,
      userId: userId,
      username: username,
      displayName: displayName,
      defaultCompanyId: _prefs.getInt(_kDefaultCompanyId),
      defaultBranchId: _prefs.getInt(_kDefaultBranchId),
      activeBranchId: _prefs.getInt(_kActiveBranchId),
    );
  }

  String? get accessToken => _prefs.getString(_kAccessToken);
  String? get refreshToken => _prefs.getString(_kRefreshToken);

  bool get hasValidSession {
    final session = read();
    if (session == null) return false;
    return session.accessTokenValid;
  }

  /// True when any session (possibly expired) exists — enough for offline login.
  bool get hasAnySession => _prefs.containsKey(_kAccessToken);
}
