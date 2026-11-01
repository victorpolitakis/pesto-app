#!/usr/bin/env bash
# Builds the Morphe patcher and its Gradle settings plugin from public source into the local Maven
# repository (~/.m2), so the gboard-patches subproject builds without a GitHub token.
#
# Morphe publishes these only to GitHub Packages, which needs authentication. Building from source
# avoids the token: the patcher's own transitive dependencies (the smali fork) still come from
# JitPack, which is anonymous.
#
# Run once per machine (or after bumping the pinned refs below). Requires: git, a JDK (17+), and
# gpg configured for signing (the Morphe builds sign their publications). On a machine without gpg,
# pass a key explicitly, e.g.:
#   ./bootstrap-morphe.sh -Psigning.gnupg.keyName=<your-key>
#
# Versions produced: morphe-patcher 2.1.0, app.morphe.patches gradle plugin 2.0.0. Keep these in
# step with gradle/libs.versions.toml and settings.gradle.kts.
set -euo pipefail

PATCHER_REF="f3997dc1011b84a301c1ffe426fd9dfdd85095ba"   # morphe-patcher 2.1.0
PLUGIN_REF="c158697065aa77ee87996991281220949e603728"    # morphe-patches-gradle-plugin 2.0.0

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

clone() { # repo ref dir
  git clone --quiet "https://github.com/MorpheApp/$1" "$work/$3"
  git -C "$work/$3" checkout --quiet "$2"
}

echo "Cloning Morphe sources..."
clone morphe-patcher "$PATCHER_REF" morphe-patcher
clone morphe-patches-gradle-plugin "$PLUGIN_REF" morphe-patches-gradle-plugin

echo "Publishing morphe-patcher to mavenLocal..."
( cd "$work/morphe-patcher" && ./gradlew --no-daemon publishToMavenLocal "$@" )

echo "Publishing morphe-patches-gradle-plugin to mavenLocal..."
( cd "$work/morphe-patches-gradle-plugin" && ./gradlew --no-daemon publishToMavenLocal "$@" )

echo "Done. Artifacts are in ~/.m2/repository/app/morphe/."
