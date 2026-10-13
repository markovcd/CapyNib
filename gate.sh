#!/usr/bin/env bash
# The gate: builds the app and runs every test, including sync against a real memos server, in a
# pinned Android SDK image. CI runs exactly this. Extra arguments go to Gradle; GATE_DOCKER_ARGS
# adds docker run options (a proxy, a CA mount) for machines that need them.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_IMAGE="cimg/android:2025.10@sha256:dc1cda740cb5712732734ca8ee61fa8f35d2a0fa55fbc468ad13a52efbdf65ac"
MEMOS_IMAGE="neosmemo/memos:0.31.0@sha256:24c2707ddd8fbd2ceaa7a61ecab86a53cdcde640b24ce572b73da14b00b5785f"

run_id="capynib-gate-$$"
cleanup() {
    docker rm -f "$run_id-memos" >/dev/null 2>&1 || true
    docker network rm "$run_id" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "$run_id" >/dev/null
docker run -d --name "$run_id-memos" --network "$run_id" --network-alias memos "$MEMOS_IMAGE" >/dev/null

mkdir -p .gradle-home
# Runs as root so the build can add SDK parts it lacks; files it makes are handed back on exit.
# shellcheck disable=SC2086 # GATE_DOCKER_ARGS is a list of options.
# The alias lets the memos server call back into the tests (a fake sign-in provider).
docker run --rm --network "$run_id" --network-alias capynib-tests \
    --user root \
    -e HOST_OWNER="$(id -u):$(id -g)" \
    -e GRADLE_USER_HOME=/work/.gradle-home \
    -e MEMOS_TEST_URL=http://memos:5230 \
    -e MEMOS_TEST_REACH_BACK=capynib-tests \
    -v "$PWD:/work" -w /work \
    ${GATE_DOCKER_ARGS:-} \
    "$ANDROID_IMAGE" \
    bash -c 'trap "chown -R \$HOST_OWNER /work" EXIT
             for i in $(seq 1 30); do curl -sf http://memos:5230/api/v1/instance/profile >/dev/null && break; sleep 1; done
             ./gradlew --no-daemon --stacktrace check :app:assembleDebug :app:assembleRelease "$@"' gate "$@"
