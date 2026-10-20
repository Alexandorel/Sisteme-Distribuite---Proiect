#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q package

CP=target/classes
run() { java -cp "$CP" ro.ubm.sd.node.StorageNode "$@" & }

run --id n1 --port 7001 --dir data/n1 --coordinator
run --id n2 --port 7002 --dir data/n2
run --id n3 --port 7003 --dir data/n3

wait
