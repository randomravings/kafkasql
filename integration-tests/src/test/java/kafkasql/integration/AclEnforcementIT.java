package kafkasql.integration;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify ACL enforcement is actually applied by Kafka.
 * <p>
 * Uses a container with TWO listeners:
 * <ul>
 *   <li>PLAINTEXT on {@value #PLAIN_PORT} — admin operations (User:ANONYMOUS super-user)</li>
 *   <li>SASLSCRAM on {@value #SASL_PORT}  — restricted users (SCRAM-SHA-256)</li>
 * </ul>
 * {@code ALLOW_EVERYONE_IF_NO_ACL_FOUND=false} ensures that SASL users without an explicit
 * ACL are denied.  Each test grants a privilege, verifies the allowed operation succeeds and
 * the disallowed one fails, then revokes the privilege.
 * <p>
 * Covers: READ / WRITE / CREATE / MODIFY on both STREAM (explicit/LITERAL) and CONTEXT
 * (prefix/PREFIXED) targets.
 * <p>
 * <b>Note</b>: Uses fixed host ports ({@value #PLAIN_PORT}, {@value #SASL_PORT}).
 * If those ports are already in use the container will fail to start.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AclEnforcementIT {

    /** PLAINTEXT bootstrap port (admin / super-user connections). */
    static final int PLAIN_PORT = 59092;

    /** SASL_PLAINTEXT bootstrap port (SCRAM-SHA-256 user connections). */
    static final int SASL_PORT = 59094;

    // ── Container ─────────────────────────────────────────────────────────────

    @SuppressWarnings("resource")
    @Container
    static final FixedHostPortGenericContainer<?> kafka = buildKafka();

    // ── Shared state ──────────────────────────────────────────────────────────

    private static AdminClient admin;
    private static KafkaEngine engine;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeAll
    static void startCluster() throws Exception {
        admin  = AdminClient.create(plainAdminProps());
        engine = new KafkaEngine("localhost:" + PLAIN_PORT);

        // SCRAM users — password == username for simplicity
        createUsers("alice", "bob", "carol", "dave", "eve", "frank", "gwen", "hank", "noacl");

        // Pre-create all topics that must exist before tests run.
        // CREATE-test topics are intentionally absent — those tests create them.
        createTopics(
            "enf.read",
            "enf.write",
            "enf.modify",
            "enf.pfx.read.A",  "enf.pfx.read.B",
            "enf.pfx.write.A", "enf.pfx.write.B",
            "enf.pfx.modify.topic"
        );

        // Seed one record so the READ test has something to fetch
        seedRecord("enf.read");
    }

    @AfterAll
    static void stopCluster() {
        if (engine != null) engine.close();
        if (admin  != null) admin.close();
    }

    // ====================================================================
    // Test 1: GRANT READ ON STREAM (explicit) — can consume, cannot produce
    // ====================================================================

    @Test
    @Order(1)
    void stream_read_explicit_allowsConsumeBlocksProduce() throws Exception {
        engine.execute("GRANT READ ON STREAM enf.read TO 'alice'");

        // positive: alice can consume
        try (var c = saslConsumer("alice", "alice", "g-alice-read")) {
            c.assign(List.of(new TopicPartition("enf.read", 0)));
            assertDoesNotThrow(() -> c.poll(Duration.ofSeconds(5)),
                "alice (READ) should be able to consume from enf.read");
        }

        // negative: alice cannot produce (no WRITE ACL)
        try (var p = saslProducer("alice", "alice")) {
            var ex = assertThrows(Exception.class,
                () -> p.send(new ProducerRecord<>("enf.read", "k", "v")).get(5, TimeUnit.SECONDS),
                "alice (READ only) must not be able to produce to enf.read");
            assertTrue(ex instanceof ExecutionException || ex instanceof TopicAuthorizationException,
                "Expected authorization failure, got: " + ex.getClass().getSimpleName());
        }

        engine.execute("REVOKE READ ON STREAM enf.read FROM 'alice'");
    }

    // ====================================================================
    // Test 2: GRANT WRITE ON STREAM (explicit) — can produce, cannot consume
    // ====================================================================

    @Test
    @Order(2)
    void stream_write_explicit_allowsProduceBlocksConsume() throws Exception {
        engine.execute("GRANT WRITE ON STREAM enf.write TO 'bob'");

        // positive: bob can produce
        try (var p = saslProducer("bob", "bob")) {
            assertDoesNotThrow(
                () -> p.send(new ProducerRecord<>("enf.write", "k", "v")).get(5, TimeUnit.SECONDS),
                "bob (WRITE) should be able to produce to enf.write");
        }

        // negative: bob cannot consume (no READ ACL)
        try (var c = saslConsumer("bob", "bob", "g-bob-write")) {
            c.assign(List.of(new TopicPartition("enf.write", 0)));
            assertThrows(TopicAuthorizationException.class,
                () -> { for (int i = 0; i < 4; i++) c.poll(Duration.ofSeconds(2)); },
                "bob (WRITE only) must not be able to consume from enf.write");
        }

        engine.execute("REVOKE WRITE ON STREAM enf.write FROM 'bob'");
    }

    // ====================================================================
    // Test 3: GRANT CREATE ON STREAM (explicit) — can create topic
    // ====================================================================

    @Test
    @Order(3)
    void stream_create_explicit_allowsCreateTopic() throws Exception {
        engine.execute("GRANT CREATE ON STREAM enf.carol.new TO 'carol'");

        // positive: carol can create the authorized topic
        try (var a = saslAdmin("carol", "carol")) {
            assertDoesNotThrow(
                () -> a.createTopics(List.of(new NewTopic("enf.carol.new", 1, (short) 1)))
                       .all().get(10, TimeUnit.SECONDS),
                "carol (CREATE on enf.carol.new) should be able to create that topic");
        }

        // negative: carol cannot create a different topic
        try (var a = saslAdmin("carol", "carol")) {
            var ex = assertThrows(ExecutionException.class,
                () -> a.createTopics(List.of(new NewTopic("enf.carol.unauthorized", 1, (short) 1)))
                       .all().get(10, TimeUnit.SECONDS),
                "carol must not create topics outside her ACL");
            assertInstanceOf(TopicAuthorizationException.class, ex.getCause());
        }

        engine.execute("REVOKE CREATE ON STREAM enf.carol.new FROM 'carol'");
    }

    // ====================================================================
    // Test 4: GRANT MODIFY ON STREAM (explicit) — can add partitions
    //         (ALTER ACL, tested via AdminClient.createPartitions)
    // ====================================================================

    @Test
    @Order(4)
    void stream_modify_explicit_allowsAlterTopic() throws Exception {
        engine.execute("GRANT MODIFY ON STREAM enf.modify TO 'dave'");

        // positive: dave can add partitions (createPartitions requires ALTER ACL)
        try (var a = saslAdmin("dave", "dave")) {
            assertDoesNotThrow(
                () -> a.createPartitions(Map.of("enf.modify", NewPartitions.increaseTo(2)))
                       .all().get(10, TimeUnit.SECONDS),
                "dave (MODIFY) should be able to add partitions to enf.modify");
        }

        // negative: noacl user cannot add partitions
        try (var a = saslAdmin("noacl", "noacl")) {
            var ex = assertThrows(ExecutionException.class,
                () -> a.createPartitions(Map.of("enf.modify", NewPartitions.increaseTo(3)))
                       .all().get(10, TimeUnit.SECONDS),
                "noacl user must not be able to add partitions");
            assertInstanceOf(TopicAuthorizationException.class, ex.getCause());
        }

        engine.execute("REVOKE MODIFY ON STREAM enf.modify FROM 'dave'");
    }

    // ====================================================================
    // Test 5: GRANT READ ON CONTEXT (prefix) — can consume from any prefixed topic
    // ====================================================================

    @Test
    @Order(5)
    void context_read_prefix_appliesAcrossTopics() throws Exception {
        engine.execute("GRANT READ ON CONTEXT enf.pfx.read TO 'eve'");

        // positive: eve can consume from BOTH enf.pfx.read.A and enf.pfx.read.B
        for (String topic : List.of("enf.pfx.read.A", "enf.pfx.read.B")) {
            try (var c = saslConsumer("eve", "eve", "g-eve-" + topic.replace('.', '-'))) {
                c.assign(List.of(new TopicPartition(topic, 0)));
                assertDoesNotThrow(() -> c.poll(Duration.ofSeconds(5)),
                    "eve (READ on context) should be able to consume from " + topic);
            }
        }

        // negative: eve cannot produce (no WRITE ACL on the prefix)
        try (var p = saslProducer("eve", "eve")) {
            var ex = assertThrows(Exception.class,
                () -> p.send(new ProducerRecord<>("enf.pfx.read.A", "k", "v")).get(5, TimeUnit.SECONDS),
                "eve (READ only) must not produce to enf.pfx.read.A");
            assertTrue(ex instanceof ExecutionException || ex instanceof TopicAuthorizationException);
        }

        engine.execute("REVOKE READ ON CONTEXT enf.pfx.read FROM 'eve'");
    }

    // ====================================================================
    // Test 6: GRANT WRITE ON CONTEXT (prefix) — can produce to any prefixed topic
    // ====================================================================

    @Test
    @Order(6)
    void context_write_prefix_appliesAcrossTopics() throws Exception {
        engine.execute("GRANT WRITE ON CONTEXT enf.pfx.write TO 'frank'");

        // positive: frank can produce to BOTH topics under the prefix
        try (var p = saslProducer("frank", "frank")) {
            for (String topic : List.of("enf.pfx.write.A", "enf.pfx.write.B")) {
                final String t = topic;
                assertDoesNotThrow(
                    () -> p.send(new ProducerRecord<>(t, "k", "v")).get(5, TimeUnit.SECONDS),
                    "frank (WRITE on context) should be able to produce to " + t);
            }
        }

        // negative: frank cannot consume (no READ ACL on the prefix)
        try (var c = saslConsumer("frank", "frank", "g-frank-write")) {
            c.assign(List.of(new TopicPartition("enf.pfx.write.A", 0)));
            assertThrows(TopicAuthorizationException.class,
                () -> { for (int i = 0; i < 4; i++) c.poll(Duration.ofSeconds(2)); },
                "frank (WRITE only) must not consume from enf.pfx.write.A");
        }

        engine.execute("REVOKE WRITE ON CONTEXT enf.pfx.write FROM 'frank'");
    }

    // ====================================================================
    // Test 7: GRANT CREATE ON CONTEXT (prefix) — can create prefixed topics
    // ====================================================================

    @Test
    @Order(7)
    void context_create_prefix_allowsCreatePrefixedTopics() throws Exception {
        engine.execute("GRANT CREATE ON CONTEXT enf.pfx.create TO 'gwen'");

        // positive: gwen can create two topics that share the prefix
        try (var a = saslAdmin("gwen", "gwen")) {
            assertDoesNotThrow(
                () -> a.createTopics(List.of(
                        new NewTopic("enf.pfx.create.X", 1, (short) 1),
                        new NewTopic("enf.pfx.create.Y", 1, (short) 1)))
                       .all().get(10, TimeUnit.SECONDS),
                "gwen (CREATE on context) should be able to create enf.pfx.create.X/Y");
        }

        // negative: gwen cannot create a topic outside her prefix
        try (var a = saslAdmin("gwen", "gwen")) {
            var ex = assertThrows(ExecutionException.class,
                () -> a.createTopics(List.of(new NewTopic("other.unauthorized.topic", 1, (short) 1)))
                       .all().get(10, TimeUnit.SECONDS),
                "gwen must not create topics outside her prefix ACL");
            assertInstanceOf(TopicAuthorizationException.class, ex.getCause());
        }

        engine.execute("REVOKE CREATE ON CONTEXT enf.pfx.create FROM 'gwen'");
    }

    // ====================================================================
    // Test 8: GRANT MODIFY ON CONTEXT (prefix) — can alter prefixed topics
    // ====================================================================

    @Test
    @Order(8)
    void context_modify_prefix_allowsAlterPrefixedTopics() throws Exception {
        engine.execute("GRANT MODIFY ON CONTEXT enf.pfx.modify TO 'hank'");

        // positive: hank can add partitions to a topic under the prefix
        try (var a = saslAdmin("hank", "hank")) {
            assertDoesNotThrow(
                () -> a.createPartitions(
                        Map.of("enf.pfx.modify.topic", NewPartitions.increaseTo(2)))
                       .all().get(10, TimeUnit.SECONDS),
                "hank (MODIFY on context) should be able to add partitions to enf.pfx.modify.topic");
        }

        // negative: hank cannot modify a topic outside his prefix
        try (var a = saslAdmin("hank", "hank")) {
            var ex = assertThrows(ExecutionException.class,
                () -> a.createPartitions(Map.of("enf.modify", NewPartitions.increaseTo(4)))
                       .all().get(10, TimeUnit.SECONDS),
                "hank must not modify topics outside his prefix ACL");
            assertInstanceOf(TopicAuthorizationException.class, ex.getCause());
        }

        engine.execute("REVOKE MODIFY ON CONTEXT enf.pfx.modify FROM 'hank'");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings({"resource", "deprecation"})
    private static FixedHostPortGenericContainer<?> buildKafka() {
        return new FixedHostPortGenericContainer<>("apache/kafka:4.0.0")
            .withFixedExposedPort(PLAIN_PORT, 9092)
            .withFixedExposedPort(SASL_PORT,  9094)
            // KRaft mode
            .withEnv("KAFKA_NODE_ID", "1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093")
            .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
            // Listeners
            .withEnv("KAFKA_LISTENERS",
                "PLAINTEXT://0.0.0.0:9092,SASLSCRAM://0.0.0.0:9094,CONTROLLER://0.0.0.0:9093")
            .withEnv("KAFKA_ADVERTISED_LISTENERS",
                "PLAINTEXT://localhost:" + PLAIN_PORT + ",SASLSCRAM://localhost:" + SASL_PORT)
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                "PLAINTEXT:PLAINTEXT,SASLSCRAM:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            // SASL / SCRAM-SHA-256
            .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "SCRAM-SHA-256")
            .withEnv("KAFKA_LISTENER_NAME_SASLSCRAM_SASL_ENABLED_MECHANISMS", "SCRAM-SHA-256")
            .withEnv("KAFKA_OPTS",
                "-Djava.security.auth.login.config=/etc/kafka/secrets/kafka_server_jaas.conf")
            // ACL authorizer — enforce ACLs, no default allow
            .withEnv("KAFKA_AUTHORIZER_CLASS_NAME",
                "org.apache.kafka.metadata.authorizer.StandardAuthorizer")
            .withEnv("KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND", "false")
            .withEnv("KAFKA_SUPER_USERS", "User:ANONYMOUS")
            // Cluster settings
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            // JAAS file (broker needs this to know ScramLoginModule for the SASLSCRAM listener)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("enf-kafka-jaas.conf"),
                "/etc/kafka/secrets/kafka_server_jaas.conf")
            .waitingFor(Wait.forLogMessage(".*started.*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    private static Properties plainAdminProps() {
        var p = new Properties();
        p.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + PLAIN_PORT);
        return p;
    }

    private static Properties saslClientProps(String username, String password) {
        var p = new Properties();
        p.put("bootstrap.servers", "localhost:" + SASL_PORT);
        p.put("security.protocol", "SASL_PLAINTEXT");
        p.put("sasl.mechanism", "SCRAM-SHA-256");
        p.put("sasl.jaas.config", String.format(
            "org.apache.kafka.common.security.scram.ScramLoginModule required " +
            "username=\"%s\" password=\"%s\";", username, password));
        return p;
    }

    private static AdminClient saslAdmin(String user, String pass) {
        return AdminClient.create(saslClientProps(user, pass));
    }

    private static KafkaProducer<String, String> saslProducer(String user, String pass) {
        var p = saslClientProps(user, pass);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,        "5000");
        p.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,  "5000");
        p.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "6000");
        return new KafkaProducer<>(p);
    }

    private static KafkaConsumer<String, String> saslConsumer(String user, String pass, String groupId) {
        var p = saslClientProps(user, pass);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.GROUP_ID_CONFIG,             groupId);
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,    "earliest");
        p.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");
        return new KafkaConsumer<>(p);
    }

    private static void createUsers(String... names) throws Exception {
        var alterations = Arrays.stream(names)
            .map(name -> (UserScramCredentialAlteration) new UserScramCredentialUpsertion(
                name,
                new ScramCredentialInfo(ScramMechanism.SCRAM_SHA_256, 4096),
                name)) // password == username
            .collect(Collectors.toList());
        admin.alterUserScramCredentials(alterations).all().get(30, TimeUnit.SECONDS);
    }

    private static void createTopics(String... names) throws Exception {
        var newTopics = Arrays.stream(names)
            .map(n -> new NewTopic(n, 1, (short) 1))
            .collect(Collectors.toList());
        admin.createTopics(newTopics).all().get(30, TimeUnit.SECONDS);
    }

    private static void seedRecord(String topic) throws Exception {
        var p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,     "localhost:" + PLAIN_PORT);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (var producer = new KafkaProducer<String, String>(p)) {
            producer.send(new ProducerRecord<>(topic, "seed", "value")).get(5, TimeUnit.SECONDS);
        }
    }
}
