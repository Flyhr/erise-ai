#!/bin/sh
set -eu

cd /workspace

checksum() {
  find pom.xml src -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' \) -print \
    | sort \
    | xargs cat 2>/dev/null \
    | sha1sum \
    | awk '{print $1}'
}

stop_app() {
  if [ -n "${APP_PID:-}" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  APP_PID=""
}

start_app() {
  echo "[backend-dev] starting Spring Boot from mounted source..."
  mvn -DskipTests -Dspring-boot.run.profiles=dev spring-boot:run &
  APP_PID=$!
}

trap 'stop_app' INT TERM EXIT

mvn -q -DskipTests dependency:go-offline || true
LAST_SUM="$(checksum)"
APP_PID=""
start_app

while true; do
  sleep 2
  CURRENT_SUM="$(checksum)"
  if [ "$CURRENT_SUM" != "$LAST_SUM" ]; then
    echo "[backend-dev] source change detected, restarting..."
    LAST_SUM="$CURRENT_SUM"
    stop_app
    start_app
  fi
  if [ -n "$APP_PID" ] && ! kill -0 "$APP_PID" 2>/dev/null; then
    APP_PID=""
  fi
done
