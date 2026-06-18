#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COUNT="${COUNT:-50}"
PASSWORD="${PASSWORD:-Password1!}"
OUTPUT_FILE="${OUTPUT_FILE:-onestop-k6/data/login-users.json}"

mkdir -p "$(dirname "$OUTPUT_FILE")"

success_count=0
failure_count=0
written_count=0

printf '[\n' > "$OUTPUT_FILE"

for n in $(seq 1 "$COUNT"); do
  i=$(printf "%02d" "$n")
  email="k6buyer${i}@test.com"
  name="k6구매자${i}"
  phone=$(printf "010-0000-%04d" "$n")

  response_file=$(mktemp)
  request_file=$(mktemp)

  cat > "$request_file" <<JSON
{
  "email": "${email}",
  "password": "${PASSWORD}",
  "name": "${name}",
  "phone": "${phone}",
  "address": "서울시 테스트구 ${n}번지",
  "role": "BUYER"
}
JSON

  status=$(curl -sS \
    -o "$response_file" \
    -w "%{http_code}" \
    -X POST "${BASE_URL}/api/auth/signup" \
    -H "Content-Type: application/json" \
    --data-binary @"$request_file")

  if [[ "$status" =~ ^2 ]]; then
    echo "✅ 생성 성공: ${email}"
    success_count=$((success_count + 1))

    if [[ "$written_count" -gt 0 ]]; then
      printf ',\n' >> "$OUTPUT_FILE"
    fi

    cat >> "$OUTPUT_FILE" <<JSON
  {
    "email": "${email}",
    "password": "${PASSWORD}"
  }
JSON
    written_count=$((written_count + 1))
  else
    echo "❌ 생성 실패: ${email}, status=${status}"
    cat "$response_file"
    echo
    failure_count=$((failure_count + 1))
  fi

  rm -f "$request_file" "$response_file"
done

printf '\n]\n' >> "$OUTPUT_FILE"

echo
echo "생성 성공: ${success_count}"
echo "생성 실패: ${failure_count}"
echo "로그인 계정 파일: ${OUTPUT_FILE}"
