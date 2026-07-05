package kafkasql.integration;

import kafkasql.engine.KafkaSqlEngine;
import kafkasql.io.SchemaMarker;
import kafkasql.runtime.Name;
import kafkasql.runtime.value.StructValue;
import kafkasql.lang.syntax.ast.misc.QName;
import kafkasql.lang.syntax.ast.stmt.AclStmt;
import kafkasql.lang.syntax.ast.stmt.UserStmt;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.admin.UserScramCredentialAlteration;
import org.apache.kafka.clients.admin.UserScramCredentialDeletion;
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import kafkasql.runtime.type.StructType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * A KafkaSqlEngine backed by real Kafka topics.
 * <p>
 * WRITE statements produce records to Kafka topics.
 * READ statements consume records from Kafka topics.
 * Topics are auto-created when first referenced.
 * <p>
 * Topic naming: the fully qualified stream name is used directly
 * (e.g., stream {@code com.CustomerEvents} → topic {@code com.CustomerEvents}).
 */
public class KafkaEngine extends KafkaSqlEngine {

    private final String bootstrapServers;
    private final AdminClient adminClient;
    private final KafkaProducer<String, byte[]> producer;
    private final Set<String> createdTopics = new HashSet<>();

    // Last results for test inspection
    private List<StreamRecord> lastQueryResult = new ArrayList<>();
    private List<String> lastShowResult = new ArrayList<>();
    private String lastExplainResult = "";

    public KafkaEngine(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;

        // Admin client for topic management
        var adminProps = new Properties();
        adminProps.put("bootstrap.servers", bootstrapServers);
        this.adminClient = AdminClient.create(adminProps);

        // Shared producer
        var producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(producerProps);
    }

    @Override
    protected void writeRecord(Name streamName, String typeName, StructValue value) {
        String topic = streamName.fullName();
        ensureTopic(topic);

        try {
            // Serialize fields as a simple length-prefixed binary format
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            var fields = value.fields();
            dos.writeInt(fields.size());
            for (var entry : fields.entrySet()) {
                dos.writeUTF(entry.getKey());
                writeFieldValue(dos, entry.getValue());
            }
            dos.flush();

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, typeName, baos.toByteArray());
            // Store the type name in a header so we can reconstruct on read
            record.headers().add(new RecordHeader("typeName", typeName.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write record to topic: " + topic, e);
        }
    }

    @Override
    protected List<StreamRecord> readRecords(Name streamName) {
        String topic = streamName.fullName();
        ensureTopic(topic);

        // Create a dedicated consumer that reads from the beginning
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafkasql-read-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        List<StreamRecord> records = new ArrayList<>();

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));

            // Poll with a timeout to allow partition assignment + fetch
            int emptyPolls = 0;
            while (emptyPolls < 3) {
                ConsumerRecords<String, byte[]> batch = consumer.poll(Duration.ofMillis(500));
                if (batch.isEmpty()) {
                    emptyPolls++;
                } else {
                    emptyPolls = 0;
                    for (ConsumerRecord<String, byte[]> rec : batch) {
                        // Check for schema-change markers — skip them
                        if (SchemaMarker.isMarker(rec)) {
                            // In a streaming reader this is where we would
                            // sync the event log and re-resolve the schema.
                            continue;
                        }
                        StreamRecord sr = deserializeRecord(rec);
                        if (sr != null) {
                            records.add(sr);
                        }
                    }
                }
            }
        }

        return records;
    }

    @Override
    protected Map<Integer, Long> writeSchemaMarker(Name streamName, String typeName) {
        String topic = streamName.fullName();
        ensureTopic(topic);

        try {
            RecordMetadata metadata = SchemaMarker.write(producer, topic, typeName);
            return Map.of(metadata.partition(), metadata.offset());
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to write schema marker to topic: " + topic, e);
        }
    }

    @Override
    protected void handleQueryResult(List<StreamRecord> records) {
        this.lastQueryResult = new ArrayList<>(records);
    }

    @Override
    protected void handleShowResult(List<String> results) {
        this.lastShowResult = new ArrayList<>(results);
    }

    @Override
    protected void handleExplainResult(String explanation) {
        this.lastExplainResult = explanation;
    }

    // ========================================================================
    // Inspection
    // ========================================================================

    public List<StreamRecord> getLastQueryResult() {
        return Collections.unmodifiableList(lastQueryResult);
    }

    public List<String> getLastShowResult() {
        return Collections.unmodifiableList(lastShowResult);
    }

    public String getLastExplainResult() {
        return lastExplainResult;
    }

    // ========================================================================
    // Topic management
    // ========================================================================

    private void ensureTopic(String topic) {
        if (createdTopics.contains(topic)) return;
        try {
            adminClient.createTopics(List.of(
                new NewTopic(topic, 1, (short) 1)
            )).all().get();
        } catch (Exception e) {
            // Topic may already exist — that's fine
        }
        createdTopics.add(topic);
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    private static void writeFieldValue(DataOutputStream dos, Object value) throws Exception {
        if (value == null) {
            dos.writeByte(0); // null marker
            return;
        }
        dos.writeByte(1); // non-null marker
        switch (value) {
            case Integer i -> { dos.writeByte('I'); dos.writeInt(i); }
            case Long l -> { dos.writeByte('L'); dos.writeLong(l); }
            case String s -> { dos.writeByte('S'); dos.writeUTF(s); }
            case Boolean b -> { dos.writeByte('B'); dos.writeBoolean(b); }
            case Float f -> { dos.writeByte('F'); dos.writeFloat(f); }
            case Double d -> { dos.writeByte('D'); dos.writeDouble(d); }
            case Short sh -> { dos.writeByte('H'); dos.writeShort(sh); }
            case Byte by -> { dos.writeByte('Y'); dos.writeByte(by); }
            default -> { dos.writeByte('S'); dos.writeUTF(value.toString()); }
        }
    }

    private static Object readFieldValue(DataInputStream dis) throws Exception {
        byte marker = dis.readByte();
        if (marker == 0) return null;
        byte type = dis.readByte();
        return switch (type) {
            case 'I' -> dis.readInt();
            case 'L' -> dis.readLong();
            case 'S' -> dis.readUTF();
            case 'B' -> dis.readBoolean();
            case 'F' -> dis.readFloat();
            case 'D' -> dis.readDouble();
            case 'H' -> dis.readShort();
            case 'Y' -> dis.readByte();
            default -> throw new IllegalStateException("Unknown type tag: " + (char) type);
        };
    }

    private StreamRecord deserializeRecord(ConsumerRecord<String, byte[]> rec) {
        try {
            String typeName = rec.key();

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(rec.value()));
            int numFields = dis.readInt();
            LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
            for (int i = 0; i < numFields; i++) {
                String fieldName = dis.readUTF();
                Object fieldValue = readFieldValue(dis);
                fields.put(fieldName, fieldValue);
            }

            // Reconstruct a StructValue using a minimal StructType
            StructType type = new StructType(
                Name.of(typeName),
                new LinkedHashMap<>(), // fields schema not needed for read-back
                List.of(),
                Optional.empty()
            );
            return new StreamRecord(typeName, new StructValue(type, fields));
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================================================
    // ACL management
    // ========================================================================

    @Override
    protected void executeGrant(AclStmt stmt) {
        try {
            boolean isGrant = stmt instanceof AclStmt.Grant;
            AclStmt.Privilege privilege = switch (stmt) {
                case AclStmt.Grant  g -> g.privilege();
                case AclStmt.Revoke r -> r.privilege();
            };
            AclStmt.Target target = switch (stmt) {
                case AclStmt.Grant  g -> g.target();
                case AclStmt.Revoke r -> r.target();
            };
            QName resource = switch (stmt) {
                case AclStmt.Grant  g -> g.resource();
                case AclStmt.Revoke r -> r.resource();
            };
            String principal = switch (stmt) {
                case AclStmt.Grant  g -> g.principal();
                case AclStmt.Revoke r -> r.principal();
            };

            String topicName = resource.fullName();
            PatternType patternType = target == AclStmt.Target.CONTEXT
                ? PatternType.PREFIXED
                : PatternType.LITERAL;

            List<AclOperation> ops = switch (privilege) {
                case READ   -> List.of(AclOperation.READ);
                case WRITE  -> List.of(AclOperation.WRITE);
                case CREATE -> List.of(AclOperation.CREATE);
                case MODIFY -> List.of(AclOperation.ALTER);
                case ALL    -> List.of(
                    AclOperation.READ,
                    AclOperation.WRITE,
                    AclOperation.CREATE,
                    AclOperation.ALTER
                );
            };

            var resourcePattern = new ResourcePattern(ResourceType.TOPIC, topicName, patternType);
            String kafkaPrincipal = principal.contains(":") ? principal : "User:" + principal;

            if (isGrant) {
                List<AclBinding> bindings = new ArrayList<>();
                for (var op : ops) {
                    var entry = new AccessControlEntry(kafkaPrincipal, "*", op, AclPermissionType.ALLOW);
                    bindings.add(new AclBinding(resourcePattern, entry));
                }
                adminClient.createAcls(bindings).all().get();
            } else {
                List<AclBindingFilter> filters = new ArrayList<>();
                for (var op : ops) {
                    var entryFilter = new AccessControlEntryFilter(
                        kafkaPrincipal, "*", op, AclPermissionType.ALLOW);
                    filters.add(new AclBindingFilter(resourcePattern.toFilter(), entryFilter));
                }
                adminClient.deleteAcls(filters).all().get();
            }
        } catch (Exception ex) {
            throw new RuntimeException("ACL management failed: " + ex.getMessage(), ex);
        }
    }

    // ========================================================================
    // User management
    // ========================================================================

    @Override
    protected void executeUser(UserStmt stmt) {
        try {
            switch (stmt) {
                case UserStmt.CreateUser cu -> {
                    String password = cu.password().orElseThrow(
                        () -> new IllegalArgumentException("CREATE USER requires PASSWORD option"));
                    var info = new ScramCredentialInfo(ScramMechanism.SCRAM_SHA_256, 4096);
                    adminClient.alterUserScramCredentials(
                        List.of(new UserScramCredentialUpsertion(cu.username(), info, password))
                    ).all().get();
                }
                case UserStmt.AlterUser au -> {
                    List<ScramMechanism> targetMechs = new ArrayList<>();
                    try {
                        var desc = adminClient.describeUserScramCredentials(List.of(au.username())).all().get();
                        var userDesc = desc.get(au.username());
                        if (userDesc != null) {
                            userDesc.credentialInfos().forEach(ci -> targetMechs.add(ci.mechanism()));
                        }
                    } catch (Exception ignored) {}
                    if (targetMechs.isEmpty()) {
                        targetMechs.add(ScramMechanism.SCRAM_SHA_256);
                    }
                    List<UserScramCredentialAlteration> alterations = new ArrayList<>();
                    for (var mech : targetMechs) {
                        alterations.add(new UserScramCredentialUpsertion(
                            au.username(), new ScramCredentialInfo(mech, 4096), au.password()));
                    }
                    adminClient.alterUserScramCredentials(alterations).all().get();
                }
                case UserStmt.DropUser du -> {
                    List<UserScramCredentialAlteration> deletions = new ArrayList<>();
                    try {
                        var desc = adminClient.describeUserScramCredentials(List.of(du.username())).all().get();
                        var userDesc = desc.get(du.username());
                        if (userDesc != null) {
                            for (var cred : userDesc.credentialInfos()) {
                                deletions.add(new UserScramCredentialDeletion(du.username(), cred.mechanism()));
                            }
                        }
                    } catch (Exception ignored) {}
                    if (!deletions.isEmpty()) {
                        adminClient.alterUserScramCredentials(deletions).all().get();
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception ex) {
            throw new RuntimeException("User management failed: " + ex.getMessage(), ex);
        }
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    public void close() {
        producer.close();
        adminClient.close();
    }
}
