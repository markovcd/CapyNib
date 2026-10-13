#!/usr/bin/env bash
# The gate: builds the app and runs every test, including sync against a real memos server, in a
# pinned Android SDK image, then reruns the core and command-line tests against every other memos
# release in memos-versions.txt. CI runs exactly this. Extra arguments go to Gradle; GATE_DOCKER_ARGS
# adds docker run options (a proxy, a CA mount) for machines that need them; GATE_MEMOS_RELEASES
# replaces the releases, in the file's "VERSION IMAGE" lines (the weekly run against memos:stable).
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_IMAGE="cimg/android:2025.10@sha256:dc1cda740cb5712732734ca8ee61fa8f35d2a0fa55fbc468ad13a52efbdf65ac"

# "VERSION IMAGE" per line, oldest first; the newest gets the full build.
mapfile -t releases < <(grep -Ev '^\s*(#|$)' <<<"${GATE_MEMOS_RELEASES:-$(cat memos-versions.txt)}")
versions=()

run_id="capynib-gate-$$"
cleanup() {
    for version in "${versions[@]}"; do docker rm -f "$run_id-memos-$version" >/dev/null 2>&1 || true; done
    docker network rm "$run_id" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "$run_id" >/dev/null
for release in "${releases[@]}"; do
    read -r version image <<<"$release"
    versions+=("$version")
    docker run -d --name "$run_id-memos-$version" --network "$run_id" --network-alias "memos-$version" "$image" >/dev/null
done

mkdir -p .gradle-home
# Runs as root so the build can add SDK parts it lacks; files it makes are handed back on exit.
# The alias lets the memos server call back into the tests (a fake sign-in provider).
# shellcheck disable=SC2086 # GATE_DOCKER_ARGS is a list of options.
docker run --rm --network "$run_id" --network-alias capynib-tests \
    --user root \
    -e HOST_OWNER="$(id -u):$(id -g)" \
    -e GRADLE_USER_HOME=/work/.gradle-home \
    -e MEMOS_TEST_REACH_BACK=capynib-tests \
    -e MEMOS_VERSIONS="${versions[*]}" \
    -v "$PWD:/work" -w /work \
    ${GATE_DOCKER_ARGS:-} \
    "$ANDROID_IMAGE" \
    bash -c 'set -euo pipefail
             trap "chown -R \$HOST_OWNER /work" EXIT
             read -r -a versions <<<"$MEMOS_VERSIONS"
             newest=${versions[-1]}
             ready() { for i in $(seq 1 30); do curl -sf "http://memos-$1:5230/api/v1/instance/profile" >/dev/null && return; sleep 1; done; echo "memos $1 did not start" >&2; return 1; }
             ready "$newest"
             MEMOS_TEST_URL="http://memos-$newest:5230" ./gradlew --no-daemon --stacktrace check :app:assembleDebug :app:assembleRelease "$@"
             for version in "${versions[@]:0:${#versions[@]}-1}"; do
                 echo "== memos $version"
                 ready "$version"
                 MEMOS_TEST_URL="http://memos-$version:5230" ./gradlew --no-daemon --stacktrace :core:test :cli:test "$@"
             done' gate "$@"
