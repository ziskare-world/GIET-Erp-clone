#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p .runtime
chmod 700 .runtime
if [ ! -f .runtime/token ]; then node -e "process.stdout.write(require('crypto').randomBytes(32).toString('hex'))" > .runtime/token; fi
chmod 600 .runtime/token
if [ -f .runtime/server.pid ] && kill -0 "$(cat .runtime/server.pid)" 2>/dev/null; then exit 0; fi
export PROXY_TOKEN="$(cat .runtime/token)"
nohup node server.mjs > .runtime/server.log 2>&1 < /dev/null &
echo $! > .runtime/server.pid
