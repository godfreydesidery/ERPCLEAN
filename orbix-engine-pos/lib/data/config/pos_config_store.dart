/// Persists overridable POS configuration in SharedPreferences.
///
/// Config keys:
///   orbix.config.apiBaseUrl    — default http://localhost:8081
///   orbix.config.deviceId      — default TILL-1
///   orbix.config.priceListCode — default RETAIL (fallback DEFAULT)
///   orbix.config.sectionId     — default 1 (HQ POS section)
///
/// These defaults keep the app working against the QA Docker container
/// (docker run -p 8081:8081 orbix:qa) with zero configuration.
///
/// Values are read at startup; providers rebuild on [save].
library;

import 'package:shared_preferences/shared_preferences.dart';

const _kApiBaseUrl = 'orbix.config.apiBaseUrl';
const _kDeviceId = 'orbix.config.deviceId';
const _kPriceListCode = 'orbix.config.priceListCode';
const _kSectionId = 'orbix.config.sectionId';

const kDefaultApiBaseUrl = 'http://localhost:8081';
const kDefaultDeviceId = 'TILL-1';
/// The seeded price list name — must match what the backend bootstraps.
/// Falls back to 'DEFAULT' when the backend uses that code.
const kDefaultPriceListCode = 'RETAIL';
const kDefaultSectionId = 1;

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

  /// The active price-list code. Sell screens use this to select the correct
  /// price tier (RETAIL vs WHOLESALE vs DEFAULT).
  String get priceListCode =>
      _prefs.getString(_kPriceListCode) ?? kDefaultPriceListCode;

  /// Server-side POS section id for the branch/location.
  /// Sent in every POS_SALE outbox payload; resolved from device config
  /// rather than hard-coded in the payment flow.
  int get sectionId => _prefs.getInt(_kSectionId) ?? kDefaultSectionId;

  // ---------------------------------------------------------------------------
  // Writes
  // ---------------------------------------------------------------------------

  Future<void> saveApiBaseUrl(String url) =>
      _prefs.setString(_kApiBaseUrl, url.trimRight());

  Future<void> saveDeviceId(String id) =>
      _prefs.setString(_kDeviceId, id.trim());

  Future<void> savePriceListCode(String code) =>
      _prefs.setString(_kPriceListCode, code.trim().toUpperCase());

  Future<void> saveSectionId(int id) =>
      _prefs.setInt(_kSectionId, id);

  Future<void> reset() async {
    await _prefs.remove(_kApiBaseUrl);
    await _prefs.remove(_kDeviceId);
    await _prefs.remove(_kPriceListCode);
    await _prefs.remove(_kSectionId);
  }
}
