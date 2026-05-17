-- ISO 4217 currency catalog seed. ~70 currencies covering all G20 nations,
-- East African neighbours, and common trading partners. Default status is
-- INACTIVE; only the East-African + reserve set starts ACTIVE so a fresh
-- deployment has a sensible dropdown. Admins enable additional ones from
-- Admin → Currencies as the business expands.
--
-- Idempotent: every INSERT is guarded by WHERE NOT EXISTS so re-running
-- (or running after admin has added custom rows) is safe.
--
-- minor_unit_digits per ISO 4217 (0 for UGX/RWF/JPY/KRW/etc., 3 for BHD/
-- KWD/OMR/etc., 2 otherwise). Symbol populated only where a single
-- Unicode glyph is unambiguous; left NULL for the rest (admin can fill in).

INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'AED', 'UAE Dirham',                    NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'AED');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'ARS', 'Argentine Peso',                NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'ARS');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'AUD', 'Australian Dollar',             'A$', 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'AUD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'BDT', 'Bangladeshi Taka',              NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'BDT');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'BHD', 'Bahraini Dinar',                NULL, 3, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'BHD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'BIF', 'Burundian Franc',               NULL, 0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'BIF');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'BRL', 'Brazilian Real',                'R$', 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'BRL');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'BWP', 'Botswana Pula',                 NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'BWP');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'CAD', 'Canadian Dollar',               'C$', 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'CAD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'CDF', 'Congolese Franc',               NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'CDF');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'CHF', 'Swiss Franc',                   NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'CHF');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'CLP', 'Chilean Peso',                  NULL, 0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'CLP');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'CNY', 'Chinese Yuan',                  '¥',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'CNY');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'COP', 'Colombian Peso',                NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'COP');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'CZK', 'Czech Koruna',                  NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'CZK');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'DKK', 'Danish Krone',                  NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'DKK');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'DZD', 'Algerian Dinar',                NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'DZD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'EGP', 'Egyptian Pound',                NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'EGP');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'ETB', 'Ethiopian Birr',                NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'ETB');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'EUR', 'Euro',                          '€',  2, 'ACTIVE'   WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'EUR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'GBP', 'Pound Sterling',                '£',  2, 'ACTIVE'   WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'GBP');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'GHS', 'Ghanaian Cedi',                 NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'GHS');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'HKD', 'Hong Kong Dollar',              'HK$',2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'HKD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'HUF', 'Hungarian Forint',              NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'HUF');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'IDR', 'Indonesian Rupiah',             NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'IDR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'ILS', 'Israeli Shekel',                '₪',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'ILS');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'INR', 'Indian Rupee',                  '₹',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'INR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'ISK', 'Icelandic Krona',               NULL, 0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'ISK');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'JOD', 'Jordanian Dinar',               NULL, 3, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'JOD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'JPY', 'Japanese Yen',                  '¥',  0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'JPY');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'KES', 'Kenyan Shilling',               'KSh',2, 'ACTIVE'   WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'KES');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'KMF', 'Comorian Franc',                NULL, 0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'KMF');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'KRW', 'South Korean Won',              '₩',  0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'KRW');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'KWD', 'Kuwaiti Dinar',                 NULL, 3, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'KWD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'LKR', 'Sri Lankan Rupee',              NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'LKR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'MAD', 'Moroccan Dirham',               NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'MAD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'MUR', 'Mauritian Rupee',               NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'MUR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'MWK', 'Malawian Kwacha',               NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'MWK');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'MXN', 'Mexican Peso',                  NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'MXN');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'MYR', 'Malaysian Ringgit',             NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'MYR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'MZN', 'Mozambican Metical',            NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'MZN');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'NGN', 'Nigerian Naira',                '₦',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'NGN');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'NOK', 'Norwegian Krone',               NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'NOK');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'NPR', 'Nepalese Rupee',                NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'NPR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'NZD', 'New Zealand Dollar',            'NZ$',2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'NZD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'OMR', 'Omani Rial',                    NULL, 3, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'OMR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'PEN', 'Peruvian Sol',                  NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'PEN');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'PHP', 'Philippine Peso',               '₱',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'PHP');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'PKR', 'Pakistani Rupee',               NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'PKR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'PLN', 'Polish Zloty',                  NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'PLN');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'QAR', 'Qatari Riyal',                  NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'QAR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'RON', 'Romanian Leu',                  NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'RON');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'RUB', 'Russian Ruble',                 '₽',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'RUB');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'RWF', 'Rwandan Franc',                 'RF', 0, 'ACTIVE'   WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'RWF');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'SAR', 'Saudi Riyal',                   NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'SAR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'SCR', 'Seychellois Rupee',             NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'SCR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'SEK', 'Swedish Krona',                 NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'SEK');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'SGD', 'Singapore Dollar',              'S$', 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'SGD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'SOS', 'Somali Shilling',               NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'SOS');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'SSP', 'South Sudanese Pound',          NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'SSP');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'SZL', 'Swazi Lilangeni',               NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'SZL');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'THB', 'Thai Baht',                     '฿',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'THB');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'TND', 'Tunisian Dinar',                NULL, 3, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'TND');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'TRY', 'Turkish Lira',                  '₺',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'TRY');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'TWD', 'New Taiwan Dollar',             'NT$',2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'TWD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'TZS', 'Tanzanian Shilling',            'TSh',2, 'ACTIVE'   WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'TZS');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'UAH', 'Ukrainian Hryvnia',             '₴',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'UAH');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'UGX', 'Ugandan Shilling',              'USh',0, 'ACTIVE'   WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'UGX');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'USD', 'US Dollar',                     '$',  2, 'ACTIVE'   WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'USD');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'VND', 'Vietnamese Dong',               '₫',  0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'VND');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'XAF', 'Central African CFA Franc',     NULL, 0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'XAF');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'XOF', 'West African CFA Franc',        NULL, 0, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'XOF');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'ZAR', 'South African Rand',            'R',  2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'ZAR');
INSERT INTO currency (code, name, symbol, minor_unit_digits, status)
  SELECT 'ZMW', 'Zambian Kwacha',                NULL, 2, 'INACTIVE' WHERE NOT EXISTS (SELECT 1 FROM currency WHERE code = 'ZMW');
