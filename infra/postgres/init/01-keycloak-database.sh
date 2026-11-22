#!/bin/sh
# Runs once, when the PostgreSQL volume is created: Keycloak gets its own user and database
# on the same server (architecture, section 4).
set -eu

psql -v ON_ERROR_STOP=1 -v kc_password="$KEYCLOAK_DB_PASSWORD" \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE USER keycloak WITH PASSWORD :'kc_password';
CREATE DATABASE keycloak OWNER keycloak;
EOSQL
