#!/bin/sh
set -eu
root="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
if [ -n "${JAVA_HOME:-}" ]; then
    java_command="$JAVA_HOME/bin/java"
else
    java_command=java
fi
exec "$java_command" -classpath "$root/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"