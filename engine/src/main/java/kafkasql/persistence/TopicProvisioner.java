package kafkasql.persistence;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Idempotent Kafka topic provisioner.
 *
 * <p>Used during deployment to ensure that a Kafka topic exists for every
 * declared STREAM before any data is written to it. Topic creation is
 * idempotent — if the topic already exists the call is silently ignored.
 *
 * <p>The caller must close this instance when done (try-with-resources).
 */
public final class TopicProvisioner implements AutoCloseable {

    private final AdminClient adminClient;

    public TopicProvisioner(String bootstrapServers) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        this.adminClient = AdminClient.create(props);
    }

    /** Creates a provisioner from a pre-built properties map (e.g. with SASL credentials). */
    public TopicProvisioner(Properties props) {
        this.adminClient = AdminClient.create(props);
    }

    /**
     * Creates the topic if it does not already exist.
     *
     * @param topicName         Kafka topic name (typically the stream's FQN)
     * @param partitions        number of partitions
     * @param replicationFactor replication factor
     */
    public void ensureTopic(String topicName, int partitions, short replicationFactor) throws Exception {
        try {
            adminClient.createTopics(List.of(new NewTopic(topicName, partitions, replicationFactor)))
                .all().get();
            System.err.println("[kafkasql] created topic: " + topicName);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                // Already exists — idempotent, nothing to do
            } else {
                throw e;
            }
        }
    }

    /** Creates the topic with 1 partition and replication factor 1. */
    public void ensureTopic(String topicName) throws Exception {
        ensureTopic(topicName, 1, (short) 1);
    }

    @Override
    public void close() {
        adminClient.close();
    }
}
