import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../features/catalog/catalog_item.dart';
import '../../../features/catalog/catalog_providers.dart';
import '../../_demo/mocks.dart';

/// Supermarket mode — spreadsheet-style table is the entire workspace.
///
/// Catalog is read from local Drift ([catalogItemsProvider]).  Barcode scan
/// looks up by item code (barcode table join is handled by [CatalogRepository]
/// but the scan field here falls back to code match for now).
class SupermarketPane extends ConsumerStatefulWidget {
  const SupermarketPane({super.key});

  @override
  ConsumerState<SupermarketPane> createState() => _SupermarketPaneState();
}

class _SupermarketPaneState extends ConsumerState<SupermarketPane> {
  final _scanCtrl = TextEditingController();
  final _scanFocus = FocusNode();
  final _codeFilterCtrl = TextEditingController();
  final _codeFocus = FocusNode();
  final _nameFilterCtrl = TextEditingController();
  final _nameFocus = FocusNode();

  String _codeFilter = '';
  String _nameFilter = '';
  String? _scanError;

  // ---- Column widths -------------------------------------------------------
  static const _wScanCode = 150.0;
  static const _wCode = 110.0;
  static const _wQty = 110.0;
  static const _wPrice = 120.0;
  static const _wTotal = 130.0;
  static const _wRemove = 44.0;

  @override
  void dispose() {
    _scanCtrl.dispose();
    _scanFocus.dispose();
    _codeFilterCtrl.dispose();
    _codeFocus.dispose();
    _nameFilterCtrl.dispose();
    _nameFocus.dispose();
    super.dispose();
  }

  List<CatalogItem> _nameMatches(List<CatalogItem> items) {
    final q = _nameFilter.trim().toLowerCase();
    if (q.isEmpty) return const [];
    return items
        .where((i) =>
            i.name.toLowerCase().contains(q) ||
            i.code.toLowerCase().contains(q))
        .toList();
  }

  void _addFromSearch(CatalogItem item) {
    if (!item.hasPriceRow) {
      setState(() => _scanError = '${item.name} has no price — cannot sell');
      return;
    }
    ref.read(cartProvider.notifier).addCatalogItem(item);
    _nameFilterCtrl.clear();
    setState(() => _nameFilter = '');
    _scanFocus.requestFocus();
  }

  void _scan(String value, List<CatalogItem> items) {
    final v = value.trim();
    if (v.isEmpty) {
      _scanFocus.requestFocus();
      return;
    }
    // Look up by item code (barcode lookup through the barcodes table is a
    // TODO once the scanner integration is wired; code match covers most SKUs).
    final hit = items
            .where((i) => i.code.toLowerCase() == v.toLowerCase())
            .firstOrNull;
    if (hit == null) {
      setState(() => _scanError = 'Unknown code: $v');
    } else if (!hit.hasPriceRow) {
      setState(() => _scanError = '${hit.name} has no price — cannot sell');
    } else {
      ref.read(cartProvider.notifier).addCatalogItem(hit);
      setState(() => _scanError = null);
    }
    _scanCtrl.clear();
    _scanFocus.requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cart = ref.watch(cartProvider);
    final catalogAsync = ref.watch(catalogItemsProvider);

    final cf = _codeFilter.trim().toLowerCase();
    final nf = _nameFilter.trim().toLowerCase();
    final visible = <_VisibleRow>[];
    for (var i = 0; i < cart.length; i++) {
      final line = cart[i];
      if (cf.isNotEmpty && !line.item.code.toLowerCase().contains(cf)) {
        continue;
      }
      if (nf.isNotEmpty && !line.item.name.toLowerCase().contains(nf)) {
        continue;
      }
      visible.add(_VisibleRow(originalIndex: i, line: line));
    }

    const rightReserved = _wQty + _wPrice + _wTotal + _wRemove;

    return Padding(
      padding: const EdgeInsets.all(12),
      child: Container(
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        clipBehavior: Clip.antiAlias,
        child: catalogAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(
            child: Text('Catalog error: $e',
                style: TextStyle(color: theme.colorScheme.error)),
          ),
          data: (catalogItems) => Stack(
            fit: StackFit.expand,
            children: [
              Column(
                children: [
                  _headerRow(theme),
                  _inputRow(theme, catalogItems),
                  if (_scanError != null) _errorStrip(theme),
                  Expanded(
                    child: cart.isEmpty
                        ? _emptyState(theme,
                            'Scan a barcode in the first row, then press Enter')
                        : (visible.isEmpty
                            ? _emptyState(theme,
                                'No rows match the current filters — clear them to see ${cart.length} cart line${cart.length == 1 ? "" : "s"}.')
                            : ListView.builder(
                                itemCount: visible.length,
                                itemBuilder: (_, i) => _DataRow(
                                  originalIndex: visible[i].originalIndex,
                                  line: visible[i].line,
                                  zebra: i.isOdd,
                                  wScanCode: _wScanCode,
                                  wCode: _wCode,
                                  wQty: _wQty,
                                  wPrice: _wPrice,
                                  wTotal: _wTotal,
                                  wRemove: _wRemove,
                                ),
                              )),
                  ),
                  _footerRow(theme, cart),
                ],
              ),
              if (_nameMatches(catalogItems).isNotEmpty)
                Positioned(
                  top: 24 + 30 + (_scanError != null ? 24 : 0),
                  left: _wScanCode + _wCode,
                  right: rightReserved,
                  child: _suggestionsDropdown(theme, _nameMatches(catalogItems)),
                ),
            ],
          ),
        ),
      ),
    );
  }

  // ---- Header ---------------------------------------------------------------
  Widget _headerRow(ThemeData theme) {
    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        border: Border(bottom: BorderSide(color: theme.colorScheme.outlineVariant, width: 1.5)),
      ),
      child: Row(
        children: [
          _headerCell(theme, 'Scan code',  width: _wScanCode),
          _headerCell(theme, 'Code',       width: _wCode),
          _headerCell(theme, 'Item name'),
          _headerCell(theme, 'Qty',        width: _wQty,    align: Alignment.center),
          _headerCell(theme, 'Unit price', width: _wPrice,  align: Alignment.centerRight),
          _headerCell(theme, 'Total',      width: _wTotal,  align: Alignment.centerRight),
          _headerCell(theme, '',           width: _wRemove),
        ],
      ),
    );
  }

  Widget _headerCell(ThemeData theme, String label,
      {double? width, Alignment align = Alignment.centerLeft}) {
    final child = Container(
      height: 24,
      width: width,
      padding: const EdgeInsets.symmetric(horizontal: 8),
      decoration: BoxDecoration(
        border: Border(right: BorderSide(color: theme.colorScheme.outlineVariant)),
      ),
      alignment: align,
      child: Text(
        label.toUpperCase(),
        style: TextStyle(
          fontSize: 10.5,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.5,
          color: theme.colorScheme.onSurface,
        ),
      ),
    );
    return width == null ? Expanded(child: child) : child;
  }

  // ---- Input row ------------------------------------------------------------
  Widget _inputRow(ThemeData theme, List<CatalogItem> catalogItems) {
    final accent = theme.colorScheme.primaryContainer.withValues(alpha: 0.18);
    return Container(
      decoration: BoxDecoration(
        color: accent,
        border: Border(bottom: BorderSide(color: theme.colorScheme.outlineVariant, width: 1.5)),
      ),
      child: Row(
        children: [
          Container(
            height: 30, width: _wScanCode,
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            decoration: BoxDecoration(
              border: Border(right: BorderSide(color: theme.colorScheme.outlineVariant)),
            ),
            child: TextField(
              controller: _scanCtrl,
              focusNode: _scanFocus,
              autofocus: true,
              style: const TextStyle(fontSize: 12, fontFamily: 'monospace', fontWeight: FontWeight.w600),
              decoration: InputDecoration(
                isDense: true,
                hintText: 'scan barcode…',
                hintStyle: TextStyle(fontSize: 11, color: theme.colorScheme.outline),
                prefixIcon: Padding(
                  padding: const EdgeInsets.only(left: 2),
                  child: Icon(Icons.barcode_reader, size: 13, color: theme.colorScheme.primary),
                ),
                prefixIconConstraints: const BoxConstraints(minWidth: 18, minHeight: 18),
                contentPadding: const EdgeInsets.symmetric(horizontal: 2, vertical: 4),
                border: InputBorder.none,
                focusedBorder: InputBorder.none,
                enabledBorder: InputBorder.none,
              ),
              onSubmitted: (v) => _scan(v, catalogItems),
            ),
          ),
          Container(
            height: 30, width: _wCode,
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            decoration: BoxDecoration(
              border: Border(right: BorderSide(color: theme.colorScheme.outlineVariant)),
            ),
            child: TextField(
              controller: _codeFilterCtrl,
              focusNode: _codeFocus,
              style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
              decoration: InputDecoration(
                isDense: true,
                hintText: 'filter code…',
                hintStyle: TextStyle(fontSize: 11, color: theme.colorScheme.outline),
                prefixIcon: Padding(
                  padding: const EdgeInsets.only(left: 2),
                  child: Icon(Icons.filter_alt_outlined, size: 13, color: theme.colorScheme.onSurfaceVariant),
                ),
                prefixIconConstraints: const BoxConstraints(minWidth: 18, minHeight: 18),
                suffixIcon: _codeFilter.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.close, size: 12),
                        visualDensity: VisualDensity.compact,
                        padding: EdgeInsets.zero,
                        constraints: const BoxConstraints(minWidth: 20, minHeight: 20),
                        onPressed: () {
                          _codeFilterCtrl.clear();
                          setState(() => _codeFilter = '');
                        },
                      ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 2, vertical: 4),
                border: InputBorder.none,
                focusedBorder: InputBorder.none,
                enabledBorder: InputBorder.none,
              ),
              onChanged: (v) => setState(() => _codeFilter = v),
            ),
          ),
          Expanded(
            child: Container(
              height: 30,
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              decoration: BoxDecoration(
                border: Border(right: BorderSide(color: theme.colorScheme.outlineVariant)),
              ),
              child: TextField(
                controller: _nameFilterCtrl,
                focusNode: _nameFocus,
                style: const TextStyle(fontSize: 12),
                decoration: InputDecoration(
                  isDense: true,
                  hintText: 'filter by item name…',
                  hintStyle: TextStyle(fontSize: 11, color: theme.colorScheme.outline),
                  prefixIcon: Padding(
                    padding: const EdgeInsets.only(left: 2),
                    child: Icon(Icons.filter_alt_outlined, size: 13, color: theme.colorScheme.onSurfaceVariant),
                  ),
                  prefixIconConstraints: const BoxConstraints(minWidth: 18, minHeight: 18),
                  suffixIcon: _nameFilter.isEmpty
                      ? null
                      : IconButton(
                          icon: const Icon(Icons.close, size: 12),
                          visualDensity: VisualDensity.compact,
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(minWidth: 20, minHeight: 20),
                          onPressed: () {
                            _nameFilterCtrl.clear();
                            setState(() => _nameFilter = '');
                          },
                        ),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 2, vertical: 4),
                  border: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  enabledBorder: InputBorder.none,
                ),
                onChanged: (v) => setState(() => _nameFilter = v),
              ),
            ),
          ),
          _bordered(theme, width: _wQty),
          _bordered(theme, width: _wPrice),
          _bordered(theme, width: _wTotal),
          _bordered(theme, width: _wRemove),
        ],
      ),
    );
  }

  Widget _bordered(ThemeData theme, {required double width}) => Container(
        height: 30,
        width: width,
        decoration: BoxDecoration(
          border: Border(right: BorderSide(color: theme.colorScheme.outlineVariant)),
        ),
      );

  Widget _errorStrip(ThemeData theme) {
    return Container(
      width: double.infinity,
      color: theme.colorScheme.errorContainer.withValues(alpha: 0.45),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Row(
        children: [
          Icon(Icons.error_outline, size: 14, color: theme.colorScheme.error),
          const SizedBox(width: 6),
          Expanded(
            child: Text(_scanError!,
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.error)),
          ),
        ],
      ),
    );
  }

  // ---- Combo dropdown -------------------------------------------------------
  Widget _suggestionsDropdown(ThemeData theme, List<CatalogItem> matches) {
    final shown = matches.take(8).toList();
    return Material(
      elevation: 6,
      borderRadius: const BorderRadius.vertical(bottom: Radius.circular(6)),
      shadowColor: Colors.black54,
      child: Container(
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          border: Border.all(color: theme.colorScheme.primary.withValues(alpha: 0.4)),
          borderRadius: const BorderRadius.vertical(bottom: Radius.circular(6)),
        ),
        constraints: const BoxConstraints(maxHeight: 220),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: theme.colorScheme.primaryContainer.withValues(alpha: 0.35),
                border: Border(
                  bottom: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5)),
                ),
              ),
              child: Row(
                children: [
                  Icon(Icons.search, size: 11, color: theme.colorScheme.primary),
                  const SizedBox(width: 6),
                  Text(
                    '${matches.length} match${matches.length == 1 ? "" : "es"}',
                    style: TextStyle(
                      fontSize: 10, letterSpacing: 0.3, fontWeight: FontWeight.w600,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                  const Spacer(),
                  if (matches.length > shown.length)
                    Text('first ${shown.length}',
                        style: TextStyle(fontSize: 10, color: theme.colorScheme.outline)),
                ],
              ),
            ),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                padding: EdgeInsets.zero,
                itemCount: shown.length,
                itemBuilder: (_, i) {
                  final m = shown[i];
                  return InkWell(
                    onTap: () => _addFromSearch(m),
                    child: Container(
                      decoration: BoxDecoration(
                        border: Border(
                          bottom: BorderSide(
                              color: theme.colorScheme.outlineVariant.withValues(alpha: 0.25)),
                        ),
                      ),
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      child: Row(
                        children: [
                          Container(
                            width: 80,
                            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
                            decoration: BoxDecoration(
                              color: theme.colorScheme.surfaceContainerHigh,
                              borderRadius: BorderRadius.circular(3),
                            ),
                            child: Text(m.code,
                                style: const TextStyle(fontSize: 10.5, fontFamily: 'monospace'),
                                overflow: TextOverflow.ellipsis),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(m.name,
                                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
                                overflow: TextOverflow.ellipsis),
                          ),
                          if (m.hasPriceRow)
                            Text(money(m.price),
                                style: TextStyle(
                                  fontSize: 11, fontWeight: FontWeight.w600,
                                  color: theme.colorScheme.primary,
                                ))
                          else
                            Text('No price',
                                style: TextStyle(fontSize: 11, color: theme.colorScheme.error)),
                          Padding(
                            padding: const EdgeInsets.only(left: 6),
                            child: Icon(Icons.add_circle, size: 13, color: theme.colorScheme.primary),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _emptyState(ThemeData theme, String hint) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(40),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.qr_code_scanner, size: 56, color: theme.colorScheme.outline),
            const SizedBox(height: 12),
            Text(hint,
                style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }

  Widget _footerRow(ThemeData theme, List<CartLine> cart) {
    final lineCount = cart.length;
    final unitCount = cart.fold<double>(0, (a, l) => a + l.qty);
    final filtered = _codeFilter.isNotEmpty || _nameFilter.isNotEmpty;
    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        border: Border(top: BorderSide(color: theme.colorScheme.outlineVariant, width: 1.5)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Row(
        children: [
          Icon(Icons.list_alt, size: 14, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 6),
          Text('$lineCount line${lineCount == 1 ? "" : "s"}', style: theme.textTheme.bodySmall),
          const SizedBox(width: 14),
          Icon(Icons.shopping_basket_outlined, size: 14, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 6),
          Text(
            '${unitCount == unitCount.truncate() ? unitCount.toInt() : unitCount} item${unitCount == 1 ? "" : "s"}',
            style: theme.textTheme.bodySmall,
          ),
          const Spacer(),
          if (filtered)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: theme.colorScheme.tertiaryContainer,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text('filtered',
                  style: theme.textTheme.labelSmall
                      ?.copyWith(color: theme.colorScheme.onTertiaryContainer)),
            ),
        ],
      ),
    );
  }
}

class _VisibleRow {
  final int originalIndex;
  final CartLine line;
  const _VisibleRow({required this.originalIndex, required this.line});
}

class _DataRow extends ConsumerWidget {
  final int originalIndex;
  final CartLine line;
  final bool zebra;
  final double wScanCode, wCode, wQty, wPrice, wTotal, wRemove;
  const _DataRow({
    required this.originalIndex,
    required this.line,
    required this.zebra,
    required this.wScanCode,
    required this.wCode,
    required this.wQty,
    required this.wPrice,
    required this.wTotal,
    required this.wRemove,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final rowBg = zebra ? theme.colorScheme.surfaceContainerLow : theme.colorScheme.surface;
    return Container(
      decoration: BoxDecoration(
        color: rowBg,
        border: Border(bottom: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.7))),
      ),
      child: Row(
        children: [
          _cell(theme, width: wScanCode,
              child: Text(line.item.barcode,
                  style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
                  overflow: TextOverflow.ellipsis, maxLines: 1)),
          _cell(theme, width: wCode,
              child: Text(line.item.code,
                  style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
                  overflow: TextOverflow.ellipsis, maxLines: 1)),
          _cell(theme, expanded: true,
              child: Text(line.item.name,
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
                  overflow: TextOverflow.ellipsis, maxLines: 1)),
          _cell(theme, width: wQty, align: Alignment.center, child: _qtyStepper(context, ref, theme)),
          _cell(theme, width: wPrice, align: Alignment.centerRight,
              child: Text(money(line.item.price), style: const TextStyle(fontSize: 12))),
          _cell(theme, width: wTotal, align: Alignment.centerRight,
              child: Text(money(line.net),
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700,
                      color: theme.colorScheme.primary))),
          _cell(theme, width: wRemove, align: Alignment.center,
              child: SizedBox(
                width: 20, height: 20,
                child: IconButton(
                  padding: EdgeInsets.zero,
                  visualDensity: VisualDensity.compact,
                  tooltip: 'Remove this line',
                  icon: const Icon(Icons.close, size: 14),
                  onPressed: () => ref.read(cartProvider.notifier).remove(originalIndex),
                ),
              )),
        ],
      ),
    );
  }

  Widget _cell(ThemeData theme, {double? width, bool expanded = false,
      Alignment align = Alignment.centerLeft, required Widget child}) {
    final container = Container(
      height: 24, width: width,
      padding: const EdgeInsets.symmetric(horizontal: 8),
      decoration: BoxDecoration(
        border: Border(right: BorderSide(color: theme.colorScheme.outlineVariant)),
      ),
      alignment: align,
      child: child,
    );
    return expanded ? Expanded(child: container) : container;
  }

  Widget _qtyStepper(BuildContext context, WidgetRef ref, ThemeData theme) {
    return Container(
      height: 20,
      decoration: BoxDecoration(
        border: Border.all(color: theme.dividerColor),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          InkWell(
            borderRadius: const BorderRadius.horizontal(left: Radius.circular(4)),
            onTap: () => ref.read(cartProvider.notifier).setQty(originalIndex, line.qty - 1),
            child: const SizedBox(width: 18, height: 18, child: Icon(Icons.remove, size: 12)),
          ),
          Container(
            width: 28, alignment: Alignment.center,
            child: Text(
              line.qty == line.qty.truncate() ? line.qty.toInt().toString() : line.qty.toString(),
              style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
            ),
          ),
          InkWell(
            borderRadius: const BorderRadius.horizontal(right: Radius.circular(4)),
            onTap: () => ref.read(cartProvider.notifier).setQty(originalIndex, line.qty + 1),
            child: const SizedBox(width: 18, height: 18, child: Icon(Icons.add, size: 12)),
          ),
        ],
      ),
    );
  }
}
