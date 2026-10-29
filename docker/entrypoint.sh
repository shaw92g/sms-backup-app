#!/bin/bash
set -euo pipefail

tar --exclude='./.git' --exclude='./.gradle' --exclude='./.idea' \
    --exclude='./artifacts' --exclude='./local.properties' --exclude='*/build' \
    -C /source -cf - . | tar --no-same-owner -C /workspace -xf -
cd /workspace
chmod +x gradlew scripts/test-core.sh

if [[ "${1:-}" == "shell" ]]; then
    shift
    exec /bin/bash "$@"
fi

./scripts/test-core.sh
result=0
./gradlew --no-daemon --no-watch-fs --console=plain "$@" || result=$?

mkdir -p /output/apk /output/reports
if [[ -d app/build/outputs/apk ]]; then
    find app/build/outputs/apk -type f -name '*.apk' -exec cp '{}' /output/apk/ \;
fi
if [[ -d app/build/reports ]]; then
    find app/build/reports -maxdepth 1 -type f -name 'lint-results*' \
        -exec cp '{}' /output/reports/ \;
fi

exit "$result"