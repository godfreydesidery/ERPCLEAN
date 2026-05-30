#!/usr/bin/env bash
# Local dev/QA data seeder for the orbix:qa container.
# Recreates: a POS cashier user (branch HQ, POS.SYNC) + a small Tanzanian catalog.
# Safe to re-run after a volume wipe. NOT committed (gitignored *.local.*).
set -euo pipefail

BASE="${BASE:-http://localhost:8081/api/v1}"
ADMIN_USER="${ADMIN_USER:-rootadmin}"
ADMIN_PASS="${ADMIN_PASS:-SKp315goPN8Nb0yJtMCCD7cm}"

jqv() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
idv() { grep -o '"id":"[0-9]*"' | head -1 | grep -o '[0-9]*'; }

echo "==> Login as $ADMIN_USER"
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | jqv accessToken)
AUTH="Authorization: Bearer $TOKEN"
[ -n "$TOKEN" ] || { echo "login failed"; exit 1; }

echo "==> POS cashier role + user (branch HQ id=1)"
ROLE=$(curl -s -X POST "$BASE/roles" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"code":"POS_CASHIER","name":"POS Cashier","description":"Full POS cashier set"}')
ROLE_UID=$(echo "$ROLE" | jqv uid)
# perms: POS.MANAGE_TILL=36 SESSION_OPEN=37 SESSION_CLOSE=38 SESSION_RECONCILE=39
#        SALE_POST=41 SYNC=44 CASH_PICKUP=51 PETTY_CASH=52
curl -s -X PUT "$BASE/roles/uid/$ROLE_UID/permissions" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"permissionIds":[36,37,38,39,41,44,51,52]}' > /dev/null
curl -s -X POST "$BASE/users" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"username":"cashier","displayName":"POS Cashier","defaultBranchId":1,"password":"Cashier#2026","mustChangePassword":false}' > /dev/null
curl -s -X POST "$BASE/roles/uid/$ROLE_UID/grants" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"username":"cashier","branchId":1}' > /dev/null
echo "    cashier / Cashier#2026  (role uid $ROLE_UID)"

echo "==> Reference data (VAT, item groups, price list)"
VAT_STD=$(curl -s -X POST "$BASE/vat-groups" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"code":"STD18","name":"Standard VAT 18%","rate":0.18,"validFrom":"2024-01-01","isDefault":true}' | idv)
VAT_EX=$(curl -s -X POST "$BASE/vat-groups" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"code":"EXEMPT","name":"VAT Exempt (0%)","rate":0.00,"validFrom":"2024-01-01","isDefault":false}' | idv)
GRP_BEV=$(curl -s -X POST "$BASE/item-groups" -H "$AUTH" -H "Content-Type: application/json" -d '{"code":"BVGS","name":"Beverages"}' | idv)
GRP_STPL=$(curl -s -X POST "$BASE/item-groups" -H "$AUTH" -H "Content-Type: application/json" -d '{"code":"STPL","name":"Staples"}' | idv)
GRP_HHLD=$(curl -s -X POST "$BASE/item-groups" -H "$AUTH" -H "Content-Type: application/json" -d '{"code":"HHLD","name":"Household"}' | idv)
PL=$(curl -s -X POST "$BASE/price-lists" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"code":"RETAIL","name":"Retail Price List","currencyCode":"TZS","validFrom":"2024-01-01","isDefault":true,"taxInclusive":true}')
PL_UID=$(echo "$PL" | jqv uid); PL_ID=$(echo "$PL" | idv)

# UoM ids seeded by Flyway: EA=1, KG=4, L=6
create_item() { # code name short grp uom vat price
  local R=$(curl -s -X POST "$BASE/items" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"code\":\"$1\",\"name\":\"$2\",\"shortName\":\"$3\",\"type\":\"SELLABLE\",\"itemGroupId\":$4,\"uomId\":$5,\"vatGroupId\":$6}")
  local IID=$(echo "$R" | idv)
  curl -s -X PUT "$BASE/price-lists/uid/$PL_UID/items" -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"itemId\":$IID,\"uomId\":$5,\"price\":$7,\"effectiveFrom\":\"2024-01-01\",\"reason\":\"Initial retail seeding\"}" > /dev/null
  echo "    $1 (id=$IID) -> $7 TZS"
}

echo "==> Items"
create_item COKE500    "Coca-Cola 500ml"                    "Coke 500ml"     $GRP_BEV  1 $VAT_STD 1200
create_item AZAMMILK1L "Azam Maziwa 1L"                     "Azam Milk 1L"   $GRP_BEV  6 $VAT_STD 1800
create_item BREAD      "Mkate (Bread)"                      "Mkate"          $GRP_STPL 1 $VAT_EX  1000
create_item SUGAR1KG   "Sukari 1kg (Sugar)"                 "Sukari 1kg"     $GRP_STPL 4 $VAT_EX  2800
create_item RICE1KG    "Mchele 1kg (Rice)"                  "Mchele 1kg"     $GRP_STPL 4 $VAT_EX  3200
create_item OIL1L      "Mafuta ya Kupikia 1L (Cooking Oil)" "Cooking Oil 1L" $GRP_STPL 6 $VAT_STD 4500
create_item SOAP       "Sabuni ya Mwili (Soap Bar)"         "Soap Bar"       $GRP_HHLD 1 $VAT_STD 700
create_item UNGA2KG    "Unga wa Mahindi 2kg (Maize Flour)"  "Unga 2kg"       $GRP_STPL 4 $VAT_EX  3500
create_item MATCHES    "Kiberiti (Matches)"                 "Kiberiti"       $GRP_HHLD 1 $VAT_STD 300
create_item SALT500G   "Chumvi 500g (Salt)"                 "Chumvi 500g"    $GRP_STPL 4 $VAT_EX  600

echo "==> Open today's business day for branch HQ (required before POS can post sales)"
curl -s -X POST "$BASE/business-days?branchId=1" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"businessDate\":\"$(date +%F)\"}" > /dev/null
echo "    business day $(date +%F) opened for branch 1"

echo "==> Verify /sync/pull (cashier, change_seq must now be stamped)"
CT=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"cashier","password":"Cashier#2026"}' | jqv accessToken)
curl -s "$BASE/sync/pull?cursor=&datasets=catalog,price" \
  -H "Authorization: Bearer $CT" -H "X-Branch-Id: 1" -H "X-Orbix-Contract-Version: 1" \
  | grep -o '"code":"[^"]*"' | head -20
echo "==> Done. Price list id=$PL_ID"
