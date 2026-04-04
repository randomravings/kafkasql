package kafkasql.lsp;

/**
 * A named external Kafka cluster connection declared in {@code connections.toml}.
 *
 * <pre>
 * [connection.prod]
 * bootstrap = "broker1:9092,broker2:9092"
 * topic     = "kafkasql.schema-events"
 * </pre>
 */
public record ConnectionConfig(
    String name,
    String bootstrapServers,
    String topic
) {}
