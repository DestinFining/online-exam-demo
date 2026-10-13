#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080/api/v1}"
TOKEN="${TOKEN:?请先设置 TOKEN='登录返回的accessToken'}"
EXAM_ID="${EXAM_ID:?请先设置 EXAM_ID}"

curl -fsS "$BASE/exams/$EXAM_ID" -H "Authorization: Bearer $TOKEN" | jq .
curl -fsS -X POST "$BASE/exams/$EXAM_ID/start" -H "Authorization: Bearer $TOKEN" | jq .
