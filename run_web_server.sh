#!/usr/bin/env sh
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$SCRIPT_DIR"
mkdir -p out
javac -encoding UTF-8 -d out board/*.java card/*.java dice/*.java player/*.java square/*.java trade/*.java server/*.java
exec java -cp out ProjectOop.web.WebServerMain
