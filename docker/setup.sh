#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Starting KafkaSQL infrastructure..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

echo "Waiting for Kafka to be healthy..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" exec kafka \
  bash -c 'until kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1; do sleep 2; done'

echo "Creating SymbolEventLog topic (idempotent)..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" exec kafka \
  kafka-topics.sh --bootstrap-server localhost:9092 \
    --create \
    --if-not-exists \
    --topic SymbolEventLog \
    --partitions 1 \
    --replication-factor 1 \
    --config cleanup.policy=compact \
    --config retention.ms=-1

echo "KafkaSQL infrastructure is ready."
