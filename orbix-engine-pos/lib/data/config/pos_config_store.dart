/// Persists overridable POS configuration in SharedPreferences.
///
/// Config keys:
///   orbix.config.apiBaseUrl  — default http://localhost:8081
///   orbix.config.deviceId    — default TILL-1
///
/// These defaults keep the app working against the QA Docker container
/// (docker run -p 8081:8081 orbix:qa) with zero configuration.
///
/// Values are read at startup; providers rebuild on [save].
library;

import 'package:shared_preferences/shared_preferences.dart';

const _kApiBaseUrl = 'orbix.config.apiBaseUrl';
const _kDeviceId = 'orbix.config.deviceId';

const kDefaultApiBaseUrl = 'http://localhost:8081';
const kDefaultDeviceId = 'TILL-1';

class PosConfigStore {
  PosConfigStore(this._prefs);

  final SharedPreferences _prefs;

  // ---------------------------------------------------------------------------
  // Reads
  // ---------------------------------------------------------------------------

  String get apiBaseUrl =>
      _prefs.getString(_kApiBaseUrl) ?? kDefaultApiBaseUrl;

  String get deviceId =>
      _prefs.getString(_kDeviceId) ?? kDefaultDeviceId;

  // ---------------------------------------------------------------------------
  // Writes
  // ---------------------------------------------------------------------------

  Future<void> saveApiBaseUrl(String url) =>
      _prefs.setString(_kApiBaseUrl, url.trimRight());

  Future<void> saveDeviceId(String id) =>
      _prefs.setString(_kDeviceId, id.trim());

  Future<void> reset() async {
    await _prefs.remove(_kApiBaseUrl);
    await _prefs.remove(_kDeviceId);
  }
}
