#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -q -DskipTests package
./mvnw -q -f web/pom.xml -DskipTests package
java -jar target/qlwebsite-0.1.0.jar --spring.profiles.active=h2 &
backend_pid=$!
cleanup() {
  kill "$backend_pid" "${frontend_pid:-}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
java -jar web/target/qlwebsite-0.1.0.jar &
frontend_pid=$!
printf 'QLWebsite: http://127.0.0.1:8080\n'
while kill -0 "$backend_pid" 2>/dev/null && kill -0 "$frontend_pid" 2>/dev/null; do
  sleep 1
done
cleanup
wait "$backend_pid" "$frontend_pid" 2>/dev/null || true
