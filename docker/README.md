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

- **Kafka Version:** 4.1.1 (Apache Kafka, KRaft mode)
- **Consensus:** KRaft (no Zookeeper required)
- **Mode:** Single-node combined broker/controller

| Listener   | Port | Protocol      | Auth                   |
| ---------- | ---- | ------------- | ---------------------- |
| PLAINTEXT  | 9092 | PLAINTEXT     | none (internal / dev)  |
| SASLSCRAM  | 9094 | SASL_PLAINTEXT| SCRAM-SHA-256          |
| CONTROLLER | 9093 | PLAINTEXT     | internal only          |

### Default credentials (created by `setup.sh`)

| Username | Password       |
| -------- | -------------- |
| `admin`  | `admin-secret` |

Override at setup time via environment variables:

```bash
KAFKA_ADMIN_USER=myuser KAFKA_ADMIN_PASSWORD=mypass ./docker/setup.sh
```

### Adding more users

Use the `kafka-configs.sh` tool against the unauthenticated PLAINTEXT listener (9092):

```bash
docker exec kafkasql-kafka \
  /opt/kafka/bin/kafka-configs.sh \
    --bootstrap-server localhost:9092 \
    --alter \
    --add-config 'SCRAM-SHA-256=[iterations=4096,password=secret]' \
    --entity-type users \
    --entity-name alice
```

### Listing SCRAM users

```bash
docker exec kafkasql-kafka \
  /opt/kafka/bin/kafka-configs.sh \
    --bootstrap-server localhost:9092 \
    --describe \
    --entity-type users
```

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

### Without authentication (PLAINTEXT)

```properties
bootstrap.servers=localhost:9092
security.protocol=PLAINTEXT
```

### With SASL/SCRAM-SHA-256

```properties
bootstrap.servers=localhost:9094
security.protocol=SASL_PLAINTEXT
sasl.mechanism=SCRAM-SHA-256
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required \
  username="admin" \
  password="admin-secret";
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

1. Check if ports 9092, 9093, or 9094 are already in use:

   ```bash
   lsof -i :9092
   lsof -i :9093
   lsof -i :9094
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
