package kafkasql.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.jupiter.api.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GRANT / REVOKE statements.
 * <p>
 * Uses Testcontainers to spin up a Kafka broker with the {@code StandardAuthorizer}
 * enabled so that ACL admin operations ({@code createAcls}, {@code describeAcls},
 * {@code deleteAcls}) are fully functional.
 * <p>
 * {@code ALLOW_EVERYONE_IF_NO_ACL_FOUND=true} and {@code SUPER_USERS=User:ANONYMOUS}
 * ensure the admin client itself (connecting as the anonymous PLAINTEXT user) retains
 * full access while the authorizer is active.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AclManagementIT {

    @Container
    @SuppressWarnings("resource")
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0")
            .withEnv("KAFKA_AUTHORIZER_CLASS_NAME",
                     "org.apache.kafka.metadata.authorizer.StandardAuthorizer")
            .withEnv("KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND", "true")
            .withEnv("KAFKA_SUPER_USERS", "User:ANONYMOUS");

    private static String bootstrapServers;
    private static AdminClient adminClient;
    private KafkaEngine engine;

    @BeforeAll
    static void initCluster() {
        bootstrapServers = kafka.getBootstrapServers();
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        adminClient = AdminClient.create(props);
    }

    @AfterAll
    static void teardownCluster() {
        if (adminClient != null) adminClient.close();
    }

    @BeforeEach
    void createEngine() {
        engine = new KafkaEngine(bootstrapServers);
    }

    @AfterEach
    void closeEngine() {
        engine.close();
    }

    // ====================================================================
    // Test 1: GRANT READ ON STREAM creates a LITERAL READ ACL
    // ====================================================================

    @Test
    @Order(1)
    void grantRead_onStream_createsLiteralAcl() throws Exception {
        engine.execute("GRANT READ ON STREAM com.example.Orders TO 'alice'");

        var bindings = describeAcls("com.example.Orders", PatternType.LITERAL);
        assertTrue(
            bindings.stream().anyMatch(b ->
                b.entry().principal().equals("User:alice") &&
                b.entry().operation() == AclOperation.READ &&
                b.entry().permissionType() == AclPermissionType.ALLOW),
            "Should have LITERAL READ ACL for alice on com.example.Orders");
    }

    // ====================================================================
    // Test 2: GRANT WRITE ON STREAM creates a LITERAL WRITE ACL
    // ====================================================================

    @Test
    @Order(2)
    void grantWrite_onStream_createsLiteralAcl() throws Exception {
        engine.execute("GRANT WRITE ON STREAM com.example.Payments TO 'bob'");

        var bindings = describeAcls("com.example.Payments", PatternType.LITERAL);
        assertTrue(
            bindings.stream().anyMatch(b ->
                b.entry().principal().equals("User:bob") &&
                b.entry().operation() == AclOperation.WRITE &&
                b.entry().permissionType() == AclPermissionType.ALLOW),
            "Should have LITERAL WRITE ACL for bob on com.example.Payments");
    }

    // ====================================================================
    // Test 3: GRANT ALL ON STREAM creates both READ and WRITE ACLs
    // ====================================================================

    @Test
    @Order(3)
    void grantAll_onStream_createsBothReadAndWriteAcls() throws Exception {
        engine.execute("GRANT ALL ON STREAM com.example.Events TO 'carol'");

        var bindings = describeAcls("com.example.Events", PatternType.LITERAL);
        boolean hasRead = bindings.stream().anyMatch(b ->
            b.entry().principal().equals("User:carol") &&
            b.entry().operation() == AclOperation.READ &&
            b.entry().permissionType() == AclPermissionType.ALLOW);
        boolean hasWrite = bindings.stream().anyMatch(b ->
            b.entry().principal().equals("User:carol") &&
            b.entry().operation() == AclOperation.WRITE &&
            b.entry().permissionType() == AclPermissionType.ALLOW);

        assertTrue(hasRead,  "GRANT ALL should create READ ACL");
        assertTrue(hasWrite, "GRANT ALL should create WRITE ACL");
    }

    // ====================================================================
    // Test 4: GRANT READ ON CONTEXT creates a PREFIXED ACL
    // ====================================================================

    @Test
    @Order(4)
    void grantRead_onContext_createsPrefixedAcl() throws Exception {
        engine.execute("GRANT READ ON CONTEXT com.example TO 'dave'");

        var bindings = describeAcls("com.example", PatternType.PREFIXED);
        assertTrue(
            bindings.stream().anyMatch(b ->
                b.entry().principal().equals("User:dave") &&
                b.entry().operation() == AclOperation.READ &&
                b.entry().permissionType() == AclPermissionType.ALLOW),
            "Should have PREFIXED READ ACL for dave on com.example");
    }

    // ====================================================================
    // Test 5: REVOKE READ ON STREAM removes the specific ACL
    // ====================================================================

    @Test
    @Order(5)
    void revokeRead_onStream_removesAcl() throws Exception {
        engine.execute("GRANT READ ON STREAM com.example.Revocable TO 'eve'");
        assertFalse(
            describeAcls("com.example.Revocable", PatternType.LITERAL).isEmpty(),
            "Precondition: ACL should exist before REVOKE");

        engine.execute("REVOKE READ ON STREAM com.example.Revocable FROM 'eve'");

        var remaining = describeAcls("com.example.Revocable", PatternType.LITERAL).stream()
            .filter(b -> b.entry().principal().equals("User:eve") &&
                         b.entry().operation() == AclOperation.READ)
            .toList();
        assertTrue(remaining.isEmpty(), "READ ACL should be removed after REVOKE");
    }

    // ====================================================================
    // Test 6: REVOKE ALL removes both READ and WRITE ACLs
    // ====================================================================

    @Test
    @Order(6)
    void revokeAll_onStream_removesBothAcls() throws Exception {
        engine.execute("GRANT ALL ON STREAM com.example.Full TO 'frank'");
        engine.execute("REVOKE ALL ON STREAM com.example.Full FROM 'frank'");

        var remaining = describeAcls("com.example.Full", PatternType.LITERAL).stream()
            .filter(b -> b.entry().principal().equals("User:frank"))
            .toList();
        assertTrue(remaining.isEmpty(), "Both READ and WRITE ACLs should be removed after REVOKE ALL");
    }

    // ====================================================================
    // Test 7: Principal with explicit "User:" prefix is not doubled
    // ====================================================================

    @Test
    @Order(7)
    void grant_withExplicitUserPrefix_isNotDoubled() throws Exception {
        engine.execute("GRANT READ ON STREAM com.example.Prefixed TO 'User:grace'");

        var bindings = describeAcls("com.example.Prefixed", PatternType.LITERAL);
        // Should find exactly "User:grace", not "User:User:grace"
        assertTrue(
            bindings.stream().anyMatch(b ->
                b.entry().principal().equals("User:grace") &&
                b.entry().operation() == AclOperation.READ),
            "Explicit User: prefix should not be doubled");
        assertTrue(
            bindings.stream().noneMatch(b ->
                b.entry().principal().startsWith("User:User:")),
            "Principal must not have double User: prefix");
    }

    // ====================================================================
    // Test 8: REVOKE on a non-existent ACL is a no-op
    // ====================================================================

    @Test
    @Order(8)
    void revoke_nonExistentAcl_doesNotThrow() {
        assertDoesNotThrow(() ->
            engine.execute("REVOKE READ ON STREAM com.example.Ghost FROM 'nobody'"));
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private List<AclBinding> describeAcls(String topicName, PatternType patternType) throws Exception {
        var filter = new AclBindingFilter(
            new ResourcePatternFilter(ResourceType.TOPIC, topicName, patternType),
            AccessControlEntryFilter.ANY);
        return new ArrayList<>(adminClient.describeAcls(filter).values().get());
    }
}
