# orbix-engine-wms

Field-sales mobile app. Flutter for Android (iOS Phase 3), Drift on SQLite, offline-first.

## Run

```bash
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter run -d <android-device-id>
```

## Layout (ARCHITECTURE.md §3.2)

```
lib/
├── main.dart
├── app/                  Router, theme, app shell
├── core/                 Money, formatting, Bluetooth printing, camera scanner
├── data/
│   ├── local/            Drift schema for offline operation
│   ├── remote/           Backend API client
│   └── sync/             Outbox dispatcher
└── features/
    ├── auth/             Login
    ├── route/            Today's route, customer list
    ├── visit/            At-customer cart, payment, receipt
    ├── expense/          Sales expenses (fuel, tolls)
    ├── sales_sheet/      End-of-day submission
    └── settings/         Bluetooth printer pairing
```

## Build for distribution

```bash
flutter build apk --release        # internal Play track / sideload
flutter build appbundle --release  # Play Store
```
