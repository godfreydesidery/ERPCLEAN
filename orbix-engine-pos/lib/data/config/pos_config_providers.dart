/// Re-exports config providers from core_providers for backward compat.
/// New code should import core_providers directly.
library;

export '../core_providers.dart' show posConfigStoreProvider, apiBaseUrlProvider, deviceIdProvider;
