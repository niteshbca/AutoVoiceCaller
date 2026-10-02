#!/bin/sh
# Portable Gradle bootstrap; official distribution, no Android Studio required.
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST_DIR="$PROJECT_DIR/.gradle-dist"
GRADLE="$DIST_DIR/gradle-8.13/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  mkdir -p "$DIST_DIR"
  curl --fail --location https://services.gradle.org/distributions/gradle-8.13-bin.zip -o "$DIST_DIR/gradle.zip"
  curl --fail --location https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256 -o "$DIST_DIR/gradle.sha256"
  python3 - "$DIST_DIR" <<'PY'
import hashlib, pathlib, sys, zipfile
p=pathlib.Path(sys.argv[1]); archive=p/'gradle.zip'
expected=(p/'gradle.sha256').read_text().strip().split()[0]
assert hashlib.sha256(archive.read_bytes()).hexdigest()==expected, 'Gradle checksum mismatch'
with zipfile.ZipFile(archive) as z: z.extractall(p)
archive.unlink()
PY
  chmod +x "$GRADLE"
fi
exec "$GRADLE" -p "$PROJECT_DIR" "$@"
