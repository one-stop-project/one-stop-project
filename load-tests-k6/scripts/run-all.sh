#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
scripts=(
  auth/01_refresh_race.js
  auth/02_device_limit_race.js
  security/03_login_rate_limit.js
  security/04_ip_spoofing_ratelimit.js
  point/05_point_concurrent_charge.js
  point/06_point_concurrent_use_via_payment.js
  point/07_point_refund_idempotency.js
  point/08_point_expire_batch.js
)
for s in "${scripts[@]}"; do
  echo "============================================================"
  echo "Running $s"
  echo "============================================================"
  ./scripts/run-one.sh "$s" || echo "FAILED: $s"
  sleep 5
done
