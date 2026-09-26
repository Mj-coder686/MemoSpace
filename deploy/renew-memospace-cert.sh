#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_DIR="/home/mjie/apps/MemoSpace-main"
readonly CERT_DIR="/home/mjie/apps/certbot"
readonly FRONTEND_CONTAINER="memo-space-frontend-1"

restore_frontend() {
  /usr/bin/docker start "${FRONTEND_CONTAINER}" >/dev/null 2>&1 || true
}

trap restore_frontend EXIT
/usr/bin/docker stop "${FRONTEND_CONTAINER}" >/dev/null
/usr/bin/docker run --rm \
  -p 80:80 \
  -v "${CERT_DIR}/conf:/etc/letsencrypt" \
  -v "${CERT_DIR}/work:/var/lib/letsencrypt" \
  -v "${CERT_DIR}/logs:/var/log/letsencrypt" \
  certbot/certbot:latest renew --standalone --quiet
restore_frontend
trap - EXIT

cd "${APP_DIR}"
/usr/bin/docker compose restart frontend >/dev/null
