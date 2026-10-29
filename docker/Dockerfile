FROM eclipse-temurin:17-jdk-jammy

ARG ANDROID_SDK_LICENSES_ACCEPTED=false
ARG ANDROID_COMMAND_LINE_TOOLS_VERSION=13114758

RUN test "$ANDROID_SDK_LICENSES_ACCEPTED" = "true" || \
    { printf '%s\n' 'Accept the Android SDK license terms, then build with ANDROID_SDK_LICENSES_ACCEPTED=true.' >&2; exit 1; }

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl unzip libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/home/android/.gradle \
    HOME=/home/android
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl --fail --location --retry 3 \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMAND_LINE_TOOLS_VERSION}_latest.zip" \
        --output /tmp/android-tools.zip \
    && unzip -q /tmp/android-tools.zip -d /tmp/android-tools \
    && mv /tmp/android-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest" \
    && rm -rf /tmp/android-tools /tmp/android-tools.zip \
    && yes | sdkmanager --licenses > /dev/null

RUN sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
    && useradd --create-home --uid 1000 --shell /bin/bash android \
    && mkdir -p /home/android/.gradle /home/android/.android /workspace /output \
    && chown -R android:android /home/android /workspace /output

COPY --chmod=0555 docker/entrypoint.sh /usr/local/bin/android-build

USER 1000:1000
WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/android-build"]
CMD ["testDebugUnitTest", "assembleDebug", "lintDebug"]