// Tests for CompletedSale model used by the receipt screen (US-POS-010/012).
//
// Coverage:
// - CompletedSale carries all line/tender/total fields
// - fiscalStatus null → fiscal block should not render (omit fiscal fields)
// - fiscalStatus='FISCALIZED' → fiscal block renders with code + QR
// - fiscalStatus='PROVISIONAL' → provisional label shown
// - Mixed-tender breakdown: multiple tenders sum correctly
// - Change = tendered - total when tendered > total

import 'package:flutter_test/flutter_test.dart';
import 'package:orbix_engine_pos/features/_demo/mocks.dart';
import 'package:orbix_engine_pos/features/payment/payment_screen.dart'
    show TenderLine;

CartLine _line(String name, double price, double qty) => CartLine(
      item: MockItem(
        code: 'X', barcode: '', name: name, group: 'G', price: price, uom: 'EA',
      ),
      qty: qty,
    );

const _walkIn = MockCustomer(code: 'WALKIN', name: 'Walk-in', walkIn: true);

void main() {
  group('CompletedSale', () {
    test('carries correct line items, totals, and tenders', () {
      final lines = [
        _line('Coca-Cola 500ml', 2000, 3),
        _line('Water 1.5L', 1500, 2),
      ];
      final tenders = [
        TenderLine(method: PaymentMethod.cash, amount: 10000),
        TenderLine(method: PaymentMethod.mobileMoney, amount: 3000),
      ];
      final sale = CompletedSale(
        receiptNo: 'TILL-1-20260530-00001',
        lines: lines,
        customer: _walkIn,
        method: PaymentMethod.cash,
        subtotal: 9000,
        discount: 0,
        total: 9000,
        tendered: 13000,
        change: 4000,
        completedAt: DateTime(2026, 5, 30, 14, 0),
        tillCode: 'TILL-1',
        cashierName: 'cashier',
        branchName: 'Branch 1',
        tenders: tenders,
      );

      expect(sale.lines, hasLength(2));
      expect(sale.total, 9000);
      expect(sale.change, 4000);
      expect(sale.tenders, hasLength(2));
      expect(sale.tenders.fold<double>(0, (s, t) => s + t.amount), 13000);
    });

    test('fiscalStatus null — fiscal fields absent', () {
      final sale = CompletedSale(
        receiptNo: 'R1',
        lines: [_line('Item', 1000, 1)],
        customer: _walkIn,
        method: PaymentMethod.cash,
        subtotal: 1000,
        discount: 0,
        total: 1000,
        tendered: 1000,
        change: 0,
        completedAt: DateTime.now(),
        tillCode: 'TILL-1',
        cashierName: 'cashier',
        branchName: 'Branch 1',
        // no fiscalStatus, fiscalVerificationCode, fiscalQrPayload
      );

      expect(sale.fiscalStatus, isNull);
      expect(sale.fiscalVerificationCode, isNull);
      expect(sale.fiscalQrPayload, isNull);
    });

    test('fiscalStatus=FISCALIZED carries verification code and QR payload',
        () {
      final sale = CompletedSale(
        receiptNo: 'R2',
        lines: [_line('Item', 2000, 1)],
        customer: _walkIn,
        method: PaymentMethod.cash,
        subtotal: 2000,
        discount: 0,
        total: 2000,
        tendered: 2000,
        change: 0,
        completedAt: DateTime.now(),
        tillCode: 'TILL-1',
        cashierName: 'cashier',
        branchName: 'Branch 1',
        fiscalStatus: 'FISCALIZED',
        fiscalVerificationCode: 'ABCD1234',
        fiscalQrPayload: 'https://verify.tra.go.tz/ABCD1234',
      );

      expect(sale.fiscalStatus, 'FISCALIZED');
      expect(sale.fiscalVerificationCode, 'ABCD1234');
      expect(sale.fiscalQrPayload, isNotNull);
    });

    test('fiscalStatus=PROVISIONAL is preserved', () {
      final sale = CompletedSale(
        receiptNo: 'R3',
        lines: [_line('Item', 3000, 1)],
        customer: _walkIn,
        method: PaymentMethod.cash,
        subtotal: 3000,
        discount: 0,
        total: 3000,
        tendered: 3000,
        change: 0,
        completedAt: DateTime.now(),
        tillCode: 'TILL-1',
        cashierName: 'cashier',
        branchName: 'Branch 1',
        fiscalStatus: 'PROVISIONAL',
      );

      expect(sale.fiscalStatus, 'PROVISIONAL');
      expect(sale.fiscalQrPayload, isNull);
    });

    test('change is correctly computed from tendered - total', () {
      final sale = CompletedSale(
        receiptNo: 'R4',
        lines: [_line('X', 7000, 1)],
        customer: _walkIn,
        method: PaymentMethod.cash,
        subtotal: 7000,
        discount: 0,
        total: 7000,
        tendered: 10000,
        change: 3000,
        completedAt: DateTime.now(),
        tillCode: 'TILL-1',
        cashierName: 'cashier',
        branchName: 'Branch 1',
      );

      expect(sale.change, closeTo(sale.tendered - sale.total, 0.01));
    });

    test('single-tender sale has one TenderLine', () {
      final sale = CompletedSale(
        receiptNo: 'R5',
        lines: [_line('Y', 5000, 2)],
        customer: _walkIn,
        method: PaymentMethod.mobileMoney,
        subtotal: 10000,
        discount: 0,
        total: 10000,
        tendered: 10000,
        change: 0,
        completedAt: DateTime.now(),
        tillCode: 'TILL-1',
        cashierName: 'cashier',
        branchName: 'Branch 1',
        tenders: const [
          TenderLine(method: PaymentMethod.mobileMoney, amount: 10000),
        ],
      );

      expect(sale.tenders, hasLength(1));
      expect(sale.tenders.first.method, PaymentMethod.mobileMoney);
      expect(sale.tenders.first.amount, 10000);
    });
  });
}
