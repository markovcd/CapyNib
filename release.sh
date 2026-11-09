#!/usr/bin/env bash
# Builds the release APK and app bundle of HEAD into dist/, signed, in the gate's pinned Android SDK
# image. F-Droid builds the same commit and publishes ours only if its build matches it byte for byte
# (docs/adr/0010). The key is the alias capynib in the keystore CAPYNIB_KEYSTORE names, opened with
# CAPYNIB_KEYSTORE_PASSWORD; without them a local run signs with a throwaway key.
# With a tag, ./release.sh v1.2.3, it refuses unless HEAD is that tag's release, ready to publish.
# Publishing is the release workflow's step, never this script's.
set -euo pipefail
cd "$(dirname "$0")"

refuse() { echo "release: $*" >&2; exit 1; }

tag=${1:-}
version=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
version_code=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' app/build.gradle.kts)
[[ -n $version && -n $version_code ]] || refuse "app/build.gradle.kts has no versionName or versionCode"
[[ -z $(git status --porcelain) ]] || refuse "the tree has uncommitted changes"
notes=$(awk -v v="$version" '/^## / { p = ($2 == v); next } p' CHANGELOG.md | sed '/./,$!d')
if [[ -n $tag ]]; then
    [[ $tag == "v$version" ]] || refuse "tag $tag does not match versionName $version in app/build.gradle.kts"
    [[ $(git rev-parse -q --verify "$tag^{commit}") == $(git rev-parse HEAD) ]] || refuse "tag $tag is not on HEAD"
    [[ -n ${CAPYNIB_KEYSTORE:-} ]] || refuse "a tagged release needs the release key (CAPYNIB_KEYSTORE)"
    [[ -n $notes ]] || refuse "CHANGELOG.md has no section for $version, or it is empty"
    [[ -s fastlane/metadata/android/en-US/changelogs/$version_code.txt ]] || refuse "no store changelog for versionCode $version_code"
fi

# A second build would empty dist/ under the first.
exec 9>>.release.lock
flock -n 9 || refuse "another release build holds .release.lock: $(cat .release.lock)"
echo "pid $$ since $(date -Is)" >.release.lock

if [[ -n ${CAPYNIB_KEYSTORE:-} ]]; then
    [[ -r $CAPYNIB_KEYSTORE ]] || refuse "cannot read the keystore CAPYNIB_KEYSTORE names"
    # -e NAME with no value hands the variable over without putting the secret on a command line.
    key=(-v "$(realpath "$CAPYNIB_KEYSTORE"):/release.keystore:ro" -e CAPYNIB_KEYSTORE=/release.keystore
         -e CAPYNIB_KEYSTORE_PASSWORD)
else
    echo "release: no CAPYNIB_KEYSTORE, signing with a throwaway key" >&2
    key=(-e CAPYNIB_THROWAWAY_KEY=1)
fi

rm -rf dist
mkdir -p dist .gradle-home
# shellcheck disable=SC2086 # GATE_DOCKER_ARGS is a list of options.
docker run --rm --user root \
    -e HOST_OWNER="$(id -u):$(id -g)" \
    -e GRADLE_USER_HOME=/work/.gradle-home \
    -e VERSION="$version" \
    "${key[@]}" \
    -v "$PWD:/work" -w /work \
    ${GATE_DOCKER_ARGS:-} \
    "$(grep -Ev '^\s*(#|$)' android-image.txt)" \
    bash -c 'set -euo pipefail
             trap "chown -R \$HOST_OWNER /work" EXIT
             if [[ -n ${CAPYNIB_THROWAWAY_KEY:-} ]]; then
                 export CAPYNIB_KEYSTORE=/tmp/throwaway.keystore CAPYNIB_KEYSTORE_PASSWORD=throwaway
                 keytool -genkeypair -noprompt -keystore "$CAPYNIB_KEYSTORE" -alias capynib -dname CN=throwaway \
                     -keyalg RSA -keysize 2048 -validity 1 -storepass throwaway -keypass throwaway
             fi
             ./gradlew --no-daemon --stacktrace :app:assembleRelease :app:bundleRelease
             cp app/build/outputs/apk/release/app-release.apk "dist/capynib-$VERSION.apk"
             cp app/build/outputs/bundle/release/app-release.aab "dist/capynib-$VERSION.aab"
             apksigner=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/apksigner
             "$apksigner" verify --print-certs "dist/capynib-$VERSION.apk" | grep -i "SHA-256"'

(cd dist && sha256sum "capynib-$version.apk" "capynib-$version.aab" >SHA256SUMS)
printf '%s\n' "$notes" >dist/notes.md
echo "release: dist/capynib-$version.apk and .aab" >&2
