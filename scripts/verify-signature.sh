#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Verifies a release APK's signature and prints its signing certificate.
#
# Usage: scripts/verify-signature.sh <path/to/app.apk> [expected-sha256]
#
# expected-sha256 is the certificate's SHA-256 digest as recorded in docs/release.md, in hex,
# with or without colons, any case. When given, the APK must be signed with that certificate.
#
# apksigner comes from $APKSIGNER if set, otherwise from the newest build-tools under
# $ANDROID_HOME (or $ANDROID_SDK_ROOT).

set -euo pipefail

die() {
  echo "verify-signature: $*" >&2
  exit 1
}

[[ $# -ge 1 && $# -le 2 ]] || die "usage: $0 <path/to/app.apk> [expected-sha256]"
apk=$1
expected=${2:-}
[[ -f $apk ]] || die "APK not found: $apk"

find_apksigner() {
  if [[ -n ${APKSIGNER:-} ]]; then
    echo "$APKSIGNER"
    return
  fi
  local sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
  [[ -n $sdk ]] || die "set ANDROID_HOME or APKSIGNER"
  local dir
  dir=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
  [[ -n $dir && -x $dir/apksigner ]] || die "no apksigner under $sdk/build-tools"
  echo "$dir/apksigner"
}

apksigner=$(find_apksigner)
out=$("$apksigner" verify --verbose --print-certs "$apk" 2>&1) || {
  echo "$out" >&2
  die "apksigner rejected $apk"
}
echo "$out"

# apksigner 36 prints "Signer #1 certificate SHA-256 digest: ...". Newer versions print one line per
# signature scheme instead, such as "V2 Signer: certificate SHA-256 digest: ...", so the same
# certificate can appear more than once. Count distinct certificates.
mapfile -t digests < <(sed -n -E 's/^(Signer #[0-9]+|V[0-9.]+ Signer( #[0-9]+)?:) certificate SHA-256 digest: ([0-9a-fA-F]+)$/\3/p' <<<"$out" | tr '[:upper:]' '[:lower:]' | sort -u)
((${#digests[@]} == 1)) || die "expected exactly one signing certificate, found ${#digests[@]}"
actual=${digests[0],,}
echo "Signing certificate SHA-256: $actual"

if [[ -n $expected ]]; then
  expected=${expected//:/}
  expected=${expected,,}
  [[ $actual == "$expected" ]] || die "certificate $actual does not match the expected $expected"
  echo "Certificate matches the expected fingerprint."
fi
