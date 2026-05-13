# orbix-engine-pos

Offline-first Point of Sale for Windows tills. Flutter Desktop, Drift on SQLite.

## Run

```bash
flutter pub get
dart run build_runner build --delete-conflicting-outputs   # generates database.g.dart, freezed files
flutter run -d windows
```

## Layout (ARCHITECTURE.md §3.1)

```
lib/
├── main.dart
├── app/                  Router, theme, app shell
├── core/                 Money, formatting, hardware abstractions
├── data/
│   ├── local/            Drift schema + DAOs (database.dart)
│   ├── remote/           Backend API client
│   └── sync/             Outbox dispatcher, conflict handlers
└── features/
    ├── auth/             Login (password + fingerprint Phase 2)
    ├── till_session/     Open, X-report, close, Z-report
    ├── cart/             Build, hold, recall
    ├── payment/          Mixed tender screens
    ├── supervisor/       PIN authorisation flow
    └── settings/         Hardware config (printer, scanner, drawer)
```

## Build for distribution

```bash
flutter build windows --release
# Output: build/windows/runner/Release/
```
Wrap with MSIX / Squirrel for OTA updates (ARCHITECTURE.md §7.6).
