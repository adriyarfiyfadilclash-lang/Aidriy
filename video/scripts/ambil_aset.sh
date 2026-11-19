#!/usr/bin/env bash
# Ambil aset karakter ADRIY dari repo privat ke video/public/adri/ (folder itu tidak masuk git Aidriy).
# Tanpa aset ini video tetap jalan dengan ADRI versi vector.
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)/public/adri"
REPO="${ADRI_ASET_REPO:-https://github.com/adriyarfiyfadilclash-lang/adriy-karakter-aset}"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" pull --ff-only
else
  rm -rf "$DIR"
  git clone --depth 1 "$REPO" "$DIR"
fi
echo "aset ADRIY siap di $DIR"
