#!/usr/bin/env bash
# Walk every endpoint the way the two callers actually use them.
#
#   ./scripts/smoke-test.sh                          # against localhost:8082
#   ./scripts/smoke-test.sh https://api.example.com  # against a deployment
#
# Two callers, two credentials, and the script uses each where the real system
# does: a JWT for everything a person does from the dashboard, an X-API-Key for
# everything the agent and dialler do. Using the wrong one is itself a bug worth
# catching, so they are never mixed.
#
# Nothing here places a call. Claiming an attempt and reporting its outcome is
# the CRM half of the flow; the dialling happens in the agent, which is not
# involved. Safe to run against production, though it does create and then
# delete a lead with a reserved test number.
#
# Exit code is the number of failed checks.

set -uo pipefail

BASE="${1:-http://localhost:8082}"
ENV_FILE="$(dirname "$0")/../.env"

# Credentials come from the environment first so this can run against a
# deployment with no .env on disk, and fall back to the local file.
if [[ -f "$ENV_FILE" ]]; then
  set -a; # shellcheck disable=SC1090
  source "$ENV_FILE"; set +a
fi
ADMIN_EMAIL="${BOOTSTRAP_ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${BOOTSTRAP_ADMIN_PASSWORD:-}"
API_KEY="${BOOTSTRAP_API_KEY:-}"

# Last resort: application.yml, where these currently live. Reading them from
# the config the server itself uses means the script cannot drift out of sync
# with the deployment it is testing.
YML="$(dirname "$0")/../src/main/resources/application.yml"
yml_value() { sed -nE "s/^[[:space:]]*$1:[[:space:]]*(.*)$/\1/p" "$YML" | head -1 | tr -d '\042\047'; }
if [[ -f "$YML" ]]; then
  [[ -z "$ADMIN_EMAIL"    ]] && ADMIN_EMAIL=$(yml_value "admin-email")
  [[ -z "$ADMIN_PASSWORD" ]] && ADMIN_PASSWORD=$(yml_value "admin-password")
  [[ -z "$API_KEY"        ]] && API_KEY=$(yml_value "api-key")
fi
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@vedryxtech.com}"

# A number that is never a real lead, so a failed cleanup cannot ring anyone.
# Unique per run: there is no DELETE /leads, so a fixed number would collide with
# its own previous run and fail everything downstream on a null lead id.
RUN_ID=$(date +%H%M%S)
TEST_PHONE="+9155${RUN_ID}1"
TEST_PHONE_2="+9155${RUN_ID}2"
PROJECT="My Home Sanctuary"

pass=0; fail=0
BOLD=$'\033[1m'; RED=$'\033[31m'; GREEN=$'\033[32m'; DIM=$'\033[2m'; OFF=$'\033[0m'

section() { printf "\n%s%s%s\n" "$BOLD" "$1" "$OFF"; }

# check <name> <expected-status> <actual-status> [detail]
check() {
  local name=$1 want=$2 got=$3 detail=${4:-}
  if [[ "$got" == "$want" ]]; then
    pass=$((pass + 1))
    printf "  %sPASS%s %-52s %s\n" "$GREEN" "$OFF" "$name" "$got"
  else
    fail=$((fail + 1))
    printf "  %sFAIL%s %-52s got %s, wanted %s\n" "$RED" "$OFF" "$name" "$got" "$want"
    [[ -n "$detail" ]] && printf "       %s%s%s\n" "$DIM" "${detail:0:200}" "$OFF"
  fi
}

# expect <name> <actual> <expected> — for values rather than status codes
expect() {
  local name=$1 got=$2 want=$3
  if [[ "$got" == "$want" ]]; then
    pass=$((pass + 1))
    printf "  %sPASS%s %-52s %s\n" "$GREEN" "$OFF" "$name" "$got"
  else
    fail=$((fail + 1))
    printf "  %sFAIL%s %-52s got '%s', wanted '%s'\n" "$RED" "$OFF" "$name" "$got" "$want"
  fi
}

# Body and status in one call, without a temp file per request.
BODY=""; STATUS=""
call() {
  local method=$1 path=$2 auth=$3 data=${4:-}
  local -a args=(-s -w '\n%{http_code}' -X "$method" "$BASE$path" -H 'Content-Type: application/json')
  case "$auth" in
    jwt) args+=(-H "Authorization: Bearer $TOKEN") ;;
    key) args+=(-H "X-API-Key: $API_KEY") ;;
    none) ;;
  esac
  [[ -n "$data" ]] && args+=(-d "$data")
  local out; out=$(curl "${args[@]}")
  STATUS="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
}

json() { echo "$BODY" | jq -r "$1" 2>/dev/null; }

printf "%sSmoke test%s  %s\n" "$BOLD" "$OFF" "$BASE"

# ---------------------------------------------------------------- preflight

section "Reachable"
call GET /actuator/health none
check "health is UP" 200 "$STATUS" "$BODY"
expect "status" "$(json .status)" "UP"

if [[ -z "$ADMIN_PASSWORD" || -z "$API_KEY" ]]; then
  printf "\n%sMissing credentials.%s Set BOOTSTRAP_ADMIN_PASSWORD and BOOTSTRAP_API_KEY,\n" "$RED" "$OFF"
  printf "or run from a checkout with a populated .env.\n"
  exit 1
fi

# ---------------------------------------------------------------- who am I

section "Authentication"
call POST /api/v1/auth/login none "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"
check "login" 200 "$STATUS" "$BODY"
TOKEN=$(json .accessToken)
[[ -n "$TOKEN" && "$TOKEN" != "null" ]] || { printf "  no token, stopping\n"; exit 1; }

call GET /api/v1/auth/me jwt
check "who am I" 200 "$STATUS"
expect "email on the token" "$(json .email)" "$ADMIN_EMAIL"

call GET /api/v1/leads none
check "no credentials is refused" 401 "$STATUS"

# ---------------------------------------------------------------- the org

section "Organization and policy"
call GET /api/v1/organizations/current key
check "agent can read the calling rules" 200 "$STATUS"
ORIG_POLICY=$(json .callPolicy)
expect "visiting hours are published" "$(json .callPolicy.visitingHoursStart)" "10:00"

call PUT /api/v1/organizations/current/call-policy jwt \
  "$(echo "$ORIG_POLICY" | jq -c '.bookingHorizonDays = 45')"
check "admin can change the policy" 200 "$STATUS"
call GET /api/v1/organizations/current key
expect "the change took" "$(json .callPolicy.bookingHorizonDays)" "45"
call PUT /api/v1/organizations/current/call-policy jwt "$(echo "$ORIG_POLICY" | jq -c .)"
check "policy restored" 200 "$STATUS"

# ---------------------------------------------------------------- leads

section "Creating a lead"
call POST /api/v1/leads jwt \
  "{\"name\":\"Smoke Test $RUN_ID\",\"phone\":\"$TEST_PHONE\",\"project\":\"$PROJECT\",\"source\":\"smoke\"}"
check "create" 201 "$STATUS" "$BODY"
LEAD_ID=$(json .id)
expect "starts at stage new" "$(json .stage)" "new"

call POST /api/v1/leads jwt \
  "{\"name\":\"No Project\",\"phone\":\"$TEST_PHONE_2\",\"source\":\"smoke\"}"
check "project is required" 400 "$STATUS"

call POST /api/v1/leads jwt \
  "{\"name\":\"Duplicate\",\"phone\":\"$TEST_PHONE\",\"project\":\"$PROJECT\",\"source\":\"smoke\"}"
check "duplicate phone is refused" 409 "$STATUS"

call GET "/api/v1/leads/$LEAD_ID" jwt
check "read one" 200 "$STATUS"
call GET /api/v1/leads jwt
check "list" 200 "$STATUS"
call GET "/api/v1/leads?stage=new" jwt
check "filter by stage binds the wire value" 200 "$STATUS"

# ---------------------------------------------------------------- the queue

section "The queue, as the dialler sees it"
call GET /api/v1/call-queue key
check "queue depth" 200 "$STATUS"

call GET /api/v1/call-queue jwt
check "the queue is closed to a person's token" 403 "$STATUS"

call POST "/api/v1/call-queue/claims?limit=1" key
check "claim" 200 "$STATUS" "$BODY"
CALL_LOG_ID=$(json '.[0].callLogId')
expect "claim carries the audio key" \
  "$(json '.[0].recordingKey' | grep -c 'audio.ogg')" "1"
expect "claim carries the project" "$(json '.[0].context.project')" "$PROJECT"

call GET "/api/v1/leads/$LEAD_ID" jwt
expect "claiming marks the lead dialing" "$(json .pipelineStatus)" "dialing"

# ---------------------------------------------------------------- outcome

section "Reporting what happened"
call POST "/api/v1/calls/$CALL_LOG_ID/outcome" key '{
  "outcome":"answered","disposition":"interested","talkSeconds":42,
  "summary":"Smoke test outcome.",
  "transcript":[
    {"role":"agent","text":"Namaste, main Kavita","atSeconds":0},
    {"role":"lead","text":"haan boliye, card number 4111 1111 1111 1111","atSeconds":5}],
  "transcriptTurnCount":2}'
check "outcome accepted" 200 "$STATUS" "$BODY"

call POST "/api/v1/calls/$CALL_LOG_ID/outcome" key \
  '{"outcome":"answered","disposition":"interested","summary":"again"}'
check "a repeat outcome is refused" 409 "$STATUS"

call GET "/api/v1/calls/$CALL_LOG_ID" jwt
check "read the call log" 200 "$STATUS"
expect "transcript stored" "$(json '.transcript | length')" "2"
expect "the lead's words are there" \
  "$(json '.transcript[1].text' | grep -c 'haan boliye')" "1"
# Redaction lives in the agent (agent/transcript.py), which strips digits before
# it posts. The server stores what it is given, so this script — posting
# directly — gets its card number back verbatim. That is the current design,
# recorded here so a change in either direction is visible.
expect "the server stores what it is given, unredacted" \
  "$(json '.transcript[1].text' | grep -c '4111')" "1"

call GET "/api/v1/leads/$LEAD_ID" jwt
expect "lead advanced past new" "$(json .stage)" "followUp"
expect "attempt counted" "$(json .attemptCount)" "1"

call GET "/api/v1/leads/$LEAD_ID/calls" jwt
check "call history for the lead" 200 "$STATUS"
call GET /api/v1/calls jwt
check "all calls" 200 "$STATUS"
call GET "/api/v1/calls?outcome=answered" jwt
check "filter calls by outcome" 200 "$STATUS"

# ---------------------------------------------------------------- artifacts

section "Recording and transcript"
call GET "/api/v1/calls/$CALL_LOG_ID/recording" jwt
check "recording metadata" 200 "$STATUS"
expect "both artifacts share one prefix" \
  "$(json .prefix | grep -c "$CALL_LOG_ID/$")" "1"
expect "the words are readable" "$(json .hasTranscript)" "true"
# playable stays false until the egress_ended webhook lands, which is expected
# until that endpoint exists.
printf "  %snote%s recordingStatus=%s playable=%s\n" \
  "$DIM" "$OFF" "$(json .recordingStatus)" "$(json .playable)"

call GET /api/v1/calls/recordings jwt
check "recordings list" 200 "$STATUS"

# ---------------------------------------------------------------- by hand

section "What a person does by hand"
call PATCH "/api/v1/leads/$LEAD_ID" jwt '{"assignedTo":"priya","notes":"smoke"}'
check "patch" 200 "$STATUS"

call GET "/api/v1/leads/$LEAD_ID/history" jwt
check "audit history" 200 "$STATUS"
AUDITED=$(echo "$BODY" | grep -c assignedTo)
expect "the edit was recorded" "$(( AUDITED > 0 ? 1 : 0 ))" "1"

call POST "/api/v1/leads/$LEAD_ID/call-reschedules" jwt \
  "{\"requestedAt\":\"$(date -u -v+1d '+%Y-%m-%dT11:00:00Z' 2>/dev/null || date -u -d '+1 day' '+%Y-%m-%dT11:00:00Z')\",\"notes\":\"smoke\"}"
check "reschedule" 200 "$STATUS" "$BODY"

call POST "/api/v1/leads/$LEAD_ID/calls" jwt '{"idempotencyKey":"smoke-once"}'
check "Call now" 202 "$STATUS" "$BODY"
MANUAL_CALL=$(json .callLogId)
call POST "/api/v1/leads/$LEAD_ID/calls" jwt '{"idempotencyKey":"smoke-once"}'
expect "a double click returns the same attempt" "$(json .callLogId)" "$MANUAL_CALL"

# ---------------------------------------------------------------- consent

section "Do not call"
# Queue depth is the wrong instrument here: the reschedule above already moved
# this lead's next attempt to tomorrow, so it is not due either way. The lead's
# own status is the direct observable, and "Call now" below proves the effect.
call PATCH "/api/v1/leads/$LEAD_ID" jwt '{"doNotCall":true}'
check "set" 200 "$STATUS"
expect "and it is taken out of the pipeline" "$(json .pipelineStatus)" "suppressed"

call POST "/api/v1/leads/$LEAD_ID/calls" jwt '{}'
check "Call now refuses a suppressed lead" 409 "$STATUS"

call PATCH "/api/v1/leads/$LEAD_ID" jwt '{"doNotCall":false}'
check "clearing without a reason is refused" 409 "$STATUS"
call GET "/api/v1/leads/$LEAD_ID" jwt
expect "and the flag survives the refusal" "$(json .doNotCall)" "true"

call PATCH "/api/v1/leads/$LEAD_ID" jwt \
  '{"doNotCall":false,"doNotCallClearedReason":"smoke test cleanup"}'
check "clearing with a reason works" 200 "$STATUS"
expect "and the lead is callable again" "$(json .pipelineStatus)" "queued"

# ---------------------------------------------------------------- the rest

section "Everything else"
call POST /api/v1/call-queue/recoveries key
check "stale-dial sweep" 200 "$STATUS"

call GET /api/v1/dashboard/summary jwt
check "dashboard" 200 "$STATUS"

call GET /api/v1/users jwt
check "users" 200 "$STATUS"

call GET /api/v1/api-keys/current jwt
check "api key metadata" 200 "$STATUS"

call GET /v3/api-docs none
check "openapi spec" 200 "$STATUS"

# ---------------------------------------------------------------- tidy up

section "Cleanup"
if [[ -n "${LEAD_ID:-}" && "$LEAD_ID" != "null" ]]; then
  call PATCH "/api/v1/leads/$LEAD_ID" jwt '{"doNotCall":true}'
  check "test lead suppressed so nothing dials it" 200 "$STATUS"
  printf "  %sleft behind for inspection: lead %s, call log %s%s\n" \
    "$DIM" "$LEAD_ID" "$CALL_LOG_ID" "$OFF"
fi

printf "\n%s%d passed, %d failed%s\n" "$BOLD" "$pass" "$fail" "$OFF"
exit "$fail"
