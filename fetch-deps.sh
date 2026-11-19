#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p lib
expected=5be1ccd3228ae5fd6eed1bd6d827bac2bc65b91c20e9957d16ea65f739f15302
jar=lib/jsoup.jar
if [[ ! -f "$jar" ]]; then
    tmp=$(mktemp lib/jsoup.XXXXXX)
    trap 'rm -f "$tmp"' EXIT
    curl --fail --location --retry 2 https://repo.maven.apache.org/maven2/org/jsoup/jsoup/1.18.3/jsoup-1.18.3.jar -o "$tmp"
    printf '%s  %s\n' "$expected" "$tmp" | sha256sum -c -
    mv "$tmp" "$jar"
    trap - EXIT
fi
printf '%s  %s\n' "$expected" "$jar" | sha256sum -c -
