#!/usr/bin/env bash
# Creates synthetic users in the local Keycloak (infra/compose.yaml) and a dev-only client
# that allows the password grant, so tokens can be fetched with curl. Never run against
# staging or production. Idempotent: existing users and clients are left as they are.
#
# Usage, from the repository root, after `docker compose -f infra/compose.yaml up -d`:
#   infra/keycloak/create-dev-users.sh
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$INFRA_DIR/.env"
REALM=riaride
DEV_CLIENT=riaride-dev-cli

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE: copy infra/.env.example and set the passwords." >&2
  exit 1
fi
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a
: "${KEYCLOAK_ADMIN_PASSWORD:?set KEYCLOAK_ADMIN_PASSWORD in infra/.env}"
: "${DEV_USER_PASSWORD:?set DEV_USER_PASSWORD in infra/.env}"

# Only the local stack: refuse a Docker daemon reached over the network (DOCKER_HOST or a
# remote context), so the script cannot create these users and client on a shared server.
docker_endpoint="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)}"
case "$docker_endpoint" in
  unix://* | npipe://* | "") ;;
  *)
    echo "Refusing to run: Docker endpoint '$docker_endpoint' is not local. This script is for the local stack only." >&2
    exit 1
    ;;
esac

kcadm() {
  docker compose -f "$INFRA_DIR/compose.yaml" exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"
}

json_escape() {
  local value=${1//\\/\\\\}
  printf '%s' "${value//\"/\\\"}"
}

echo "Waiting for Keycloak..."
for _ in $(seq 1 60); do
  if kcadm config credentials --server http://localhost:8080 --realm master \
      --user "${KEYCLOAK_ADMIN_USERNAME:-admin}" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
kcadm get "realms/$REALM" --fields realm >/dev/null

# Dev-only public client with the password grant and the same token mappers as the SPA.
if [[ -z "$(kcadm get clients -r "$REALM" -q clientId="$DEV_CLIENT" --fields id --format csv --noquotes)" ]]; then
  kcadm create clients -r "$REALM" -f - <<JSON >/dev/null
{
  "clientId": "$DEV_CLIENT",
  "publicClient": true,
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": true,
  "protocolMappers": [{
    "name": "hotel_id",
    "protocol": "openid-connect",
    "protocolMapper": "oidc-usermodel-attribute-mapper",
    "config": {
      "user.attribute": "hotel_id",
      "claim.name": "hotel_id",
      "jsonType.label": "String",
      "access.token.claim": "true",
      "id.token.claim": "true"
    }
  }]
}
JSON
  echo "Created client $DEV_CLIENT"
fi

# username;first name;last name;realm role;hotel_id (empty when not a hotel manager)
USERS=(
  "manager.centro;Joana;Matos;HOTEL_MANAGER;00000000-0000-0000-0000-000000000001"
  "manager.praia;Rui;Pacheco;HOTEL_MANAGER;00000000-0000-0000-0000-000000000002"
  "manager.salinas;Marta;Cunha;HOTEL_MANAGER;00000000-0000-0000-0000-000000000003"
  "operator.rodaleve;Hugo;Simões;OPERATOR;"
  "technician.rodaleve;Sara;Lopes;TECHNICIAN;"
  "admin.riaride;Ana;Teixeira;ADMIN;"
  "guest.demo;Tomás;Ferreira;GUEST;"
)

for entry in "${USERS[@]}"; do
  IFS=';' read -r username first last role hotel <<<"$entry"
  if [[ -n "$(kcadm get users -r "$REALM" -q exact=true -q username="$username" --fields id --format csv --noquotes)" ]]; then
    echo "User $username exists, skipped"
    continue
  fi
  attributes="{}"
  if [[ -n "$hotel" ]]; then
    attributes="{\"hotel_id\": [\"$hotel\"]}"
  fi
  # The user, password included, goes through stdin, so the password never appears in a
  # process's arguments (visible with ps).
  kcadm create users -r "$REALM" -f - >/dev/null 2>&1 <<JSON
{
  "username": "$username",
  "enabled": true,
  "email": "$username@example.test",
  "emailVerified": true,
  "firstName": "$first",
  "lastName": "$last",
  "attributes": $attributes,
  "credentials": [{"type": "password", "value": "$(json_escape "$DEV_USER_PASSWORD")", "temporary": false}]
}
JSON
  kcadm add-roles -r "$REALM" --uusername "$username" --rolename "$role"
  echo "Created $username ($role)"
done

cat <<EOF2

Done. Get a token, for example:
  curl -s -d grant_type=password -d client_id=$DEV_CLIENT -d username=manager.praia \\
    --data-urlencode "password=\$DEV_USER_PASSWORD" \\
    http://localhost:8180/realms/$REALM/protocol/openid-connect/token
EOF2
