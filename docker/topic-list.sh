#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

docker exec kafkasql-kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
    --list
