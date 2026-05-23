#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ADMIN_USER="${KAFKA_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${KAFKA_ADMIN_PASSWORD:-admin-secret}"

echo "Starting KafkaSQL infrastructure..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d --wait

echo "Creating backing symbol topic (idempotent)..."
docker exec kafkasql-kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
    --create \
    --if-not-exists \
    --topic _kafkasql_log \
    --partitions 1 \
    --replication-factor 1 \
    --config cleanup.policy=compact \
    --config retention.ms=-1

echo "Creating SCRAM-SHA-256 credential for '${ADMIN_USER}'..."
for attempt in 1 2 3 4 5; do
  if docker exec kafkasql-kafka \
      timeout 30 /opt/kafka/bin/kafka-configs.sh \
        --bootstrap-server localhost:9092 \
        --alter \
        --add-config "SCRAM-SHA-256=[iterations=4096,password=${ADMIN_PASSWORD}]" \
        --entity-type users \
        --entity-name "${ADMIN_USER}"; then
    break
  fi
  if [ "$attempt" -eq 5 ]; then
    echo "ERROR: Failed to create SCRAM user after 5 attempts"
    exit 1
  fi
  echo "  Retrying user creation in 3s (attempt ${attempt}/5)..."
  sleep 3
done

echo ""
echo "KafkaSQL infrastructure is ready."
echo ""
echo "  PLAINTEXT (no auth) : localhost:9092"
echo "  SASL_SCRAM          : localhost:9094"
echo "    username : ${ADMIN_USER}"
echo "    password : ${ADMIN_PASSWORD}"
echo ""
echo "Override credentials with KAFKA_ADMIN_USER / KAFKA_ADMIN_PASSWORD env vars."
