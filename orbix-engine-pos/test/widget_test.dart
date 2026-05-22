// Smoke test — the app boots, the router wires up, and the login screen
// renders. Replaces the stock counter test scaffolded by `flutter create`.

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:orbix_engine_pos/app/app.dart';

void main() {
  testWidgets('login screen renders the sign-in form', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: OrbixPosApp()));
    // settleUntilVisible — go_router resolves the initial location asynchronously.
    await tester.pumpAndSettle();

    expect(find.text('Orbix Engine POS'), findsOneWidget);
    expect(find.text('Sign in'), findsOneWidget);
  });
}
