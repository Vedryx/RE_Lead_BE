#!/usr/bin/env bash
# Start the dashboard API locally with credentials from .env.
#
# The secrets are deliberately not in application.yml: JWT_SECRET,
# BOOTSTRAP_ADMIN_PASSWORD and BOOTSTRAP_API_KEY have no defaults, so the app
# refuses to start rather than run on a key that is readable in the repository.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  echo "No .env found. Copy .env.example to .env and fill it in." >&2
  exit 1
fi
set -a; source .env; set +a

for required in JWT_SECRET BOOTSTRAP_ADMIN_PASSWORD BOOTSTRAP_API_KEY; do
  if [[ -z "${!required:-}" ]]; then
    echo "$required is empty in .env" >&2
    exit 1
  fi
done
if (( ${#JWT_SECRET} < 32 )); then
  echo "JWT_SECRET must be at least 32 characters (got ${#JWT_SECRET})" >&2
  exit 1
fi

exec mvn spring-boot:run "$@"
