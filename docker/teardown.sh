#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Stopping KafkaSQL infrastructure..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" down -v

echo "KafkaSQL infrastructure stopped and volumes removed."
