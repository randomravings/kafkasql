package kafkasql.lsp;

import java.util.Optional;
import java.util.Properties;

/**
 * A named external Kafka cluster connection declared in {@code connections.toml}.
 *
 * <pre>
 * [connection.prod]
 * bootstrap = "broker1:9092,broker2:9092"
 * topic     = "kafkasql.schema-events"
 * username  = "alice"          # optional — enables SASL/SCRAM-SHA-256
 * password  = "secret"         # optional — required when username is set
 * </pre>
 */
public record ConnectionConfig(
    String name,
    String bootstrapServers,
    String topic,
    Optional<String> username,
    Optional<String> password
) {
    /**
     * Returns a base {@link Properties} containing {@code bootstrap.servers} and,
     * when credentials are present, the SASL/SCRAM-SHA-256 security properties.
     */
    public Properties baseProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        if (username.isPresent() && password.isPresent()) {
            props.put("security.protocol", "SASL_PLAINTEXT");
            props.put("sasl.mechanism", "SCRAM-SHA-256");
            props.put("sasl.jaas.config",
                "org.apache.kafka.common.security.scram.ScramLoginModule required "
                + "username=\"" + username.get() + "\" "
                + "password=\"" + password.get() + "\";");
        }
        return props;
    }
}
