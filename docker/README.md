# Kafka Development Environment

This directory provides a local Apache Kafka instance for development and testing.

## Quick Start

Start Kafka:
```bash
docker compose up -d
```

Stop Kafka:
```bash
docker compose down
```

Stop and remove data:
```bash
docker compose down -v
```

## Configuration

- **Kafka Version:** 4.0.0 (Apache Kafka, not Confluent)
- **Consensus:** KRaft (no Zookeeper required)
- **Mode:** Single-node combined broker/controller
- **Bootstrap Server:** `localhost:9092`

## Basic Operations

### Create a Topic

```bash
docker compose exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --topic test-topic \
  --partitions 3 \
  --replication-factor 1
```

### List Topics

```bash
docker compose exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

### Describe a Topic

```bash
docker compose exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic test-topic
```

### Produce Messages

```bash
docker compose exec kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic test-topic
```

Type messages and press Enter. Press Ctrl+C to exit.

### Consume Messages

```bash
docker compose exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic test-topic \
  --from-beginning
```

Press Ctrl+C to exit.

## Health Check

Check if Kafka is ready:
```bash
docker compose exec kafka kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092
```

Or check container health:
```bash
docker compose ps
```

## Accessing from Host Applications

Connect to Kafka from your local machine using:
- **Bootstrap Server:** `localhost:9092`
- **Security Protocol:** `PLAINTEXT` (no authentication)

Example connection string for most clients:
```
localhost:9092
```

## Troubleshooting

### View Kafka Logs

```bash
docker compose logs kafka
```

Follow logs in real-time:
```bash
docker compose logs -f kafka
```

### Container Not Starting

1. Check if port 9092 or 9093 is already in use:
   ```bash
   lsof -i :9092
   lsof -i :9093
   ```

2. Check Docker daemon is running

3. Inspect container logs for errors

### Reset Everything

To completely reset Kafka (deletes all topics and data):
```bash
docker compose down -v
docker compose up -d
```

## Notes

- **Single Node:** This setup is for development only. For production, use a multi-node cluster.
- **No Persistence Across Resets:** Data is stored in a Docker volume. Running `docker compose down -v` will delete all topics and messages.
- **KRaft Mode:** This uses Kafka's native consensus protocol (KRaft), not Zookeeper.
- **Replication Factor:** Set to 1 (suitable for single-node development only).
