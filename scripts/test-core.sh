#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
javac -d "$output" \
  app/src/main/java/com/example/smsbackup/BackupDates.java \
  app/src/main/java/com/example/smsbackup/EndpointPolicy.java \
  tests/CoreLogicTest.java
java -cp "$output" com.example.smsbackup.CoreLogicTest