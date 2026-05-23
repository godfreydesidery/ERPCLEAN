#!/usr/bin/env bash
#
# Single-container QA entrypoint. Bootstraps MariaDB on first run, then
# hands off to supervisord which runs MariaDB + Redis + the Spring Boot jar.

set -euo pipefail

DATA_DIR=/var/lib/mysql
MARKER="$DATA_DIR/.orbix-initialized"

DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-rootlocal}"
DB_NAME="${DB_NAME:-orbix_erp}"
DB_USER="${DB_USER:-orbix}"
DB_PASSWORD="${DB_PASSWORD:-orbixlocal}"

# Mounted volumes come up owned by root by default — hand the data dir to mysql.
chown -R mysql:mysql "$DATA_DIR"

# MariaDB binds a unix socket under /run/mysqld, which doesn't exist in a fresh
# container (no systemd-tmpfiles to create it). Make it before any mariadbd start
# — both the bootstrap below and the supervisord-managed mariadbd need it.
mkdir -p /run/mysqld
chown -R mysql:mysql /run/mysqld

if [ ! -f "$MARKER" ]; then
  echo "[entrypoint] First run — initialising MariaDB system tables in $DATA_DIR"
  mariadb-install-db --user=mysql --datadir="$DATA_DIR" >/dev/null

  echo "[entrypoint] Starting mariadbd for bootstrap"
  mariadbd --user=mysql --datadir="$DATA_DIR" --bind-address=127.0.0.1 &
  MARIADB_PID=$!

  echo "[entrypoint] Waiting for mariadbd to accept connections"
  for _ in $(seq 1 60); do
    if mariadb -uroot -e "SELECT 1" >/dev/null 2>&1; then break; fi
    sleep 1
  done

  echo "[entrypoint] Creating database '$DB_NAME' and user '$DB_USER'"
  mariadb -uroot <<SQL
ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_ROOT_PASSWORD}';
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

  echo "[entrypoint] Shutting down bootstrap mariadbd"
  mariadb-admin -uroot -p"${DB_ROOT_PASSWORD}" shutdown
  wait "$MARIADB_PID" || true

  touch "$MARKER"
  echo "[entrypoint] Init complete"
fi

# Wrapper that supervisor calls for the API. Waits for MariaDB to be reachable
# (supervisor starts MariaDB and the API in parallel), then exec's the jar.
cat >/opt/orbix/start-api.sh <<'SH'
#!/usr/bin/env bash
set -euo pipefail
for _ in $(seq 1 60); do
  if (echo > /dev/tcp/127.0.0.1/3306) >/dev/null 2>&1; then break; fi
  sleep 1
done
exec java \
  -XX:MaxRAMPercentage=70.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /opt/orbix/app.jar
SH
chmod +x /opt/orbix/start-api.sh

echo "[entrypoint] Handing off to supervisord"
exec /usr/bin/supervisord -c /etc/supervisor/supervisord.conf
