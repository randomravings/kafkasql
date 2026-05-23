package kafkasql.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.admin.UserScramCredentialsDescription;
import org.junit.jupiter.api.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CREATE USER / ALTER USER / DROP USER statements.
 * <p>
 * Uses Testcontainers to spin up a real Kafka broker.  SCRAM credentials are
 * managed via the Kafka AdminClient API — the broker stores them independently
 * of whether SASL is configured on its listeners.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserManagementIT {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");

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
    // Test 1: CREATE USER stores a SCRAM_SHA_256 credential
    // ====================================================================

    @Test
    @Order(1)
    void createUser_storesScramCredential() throws Exception {
        engine.execute("CREATE USER ituser1 WITH PASSWORD 'Password1!'");

        var desc = describeUser("ituser1");
        assertNotNull(desc, "Credential should exist after CREATE USER");
        assertEquals(1, desc.credentialInfos().size());
        assertEquals(ScramMechanism.SCRAM_SHA_256, desc.credentialInfos().get(0).mechanism());
    }

    // ====================================================================
    // Test 2: CREATE USER without PASSWORD raises an error
    // ====================================================================

    @Test
    @Order(2)
    void createUser_withoutPassword_throwsAtExecution() {
        var ex = assertThrows(RuntimeException.class,
            () -> engine.execute("CREATE USER ituser_nopass"));
        assertTrue(
            ex.getMessage().contains("PASSWORD") || (ex.getCause() != null && ex.getCause().getMessage().contains("PASSWORD")),
            "Exception should mention missing PASSWORD"
        );
    }

    // ====================================================================
    // Test 3: ALTER USER updates the stored credential
    // ====================================================================

    @Test
    @Order(3)
    void alterUser_updatesCredential() throws Exception {
        engine.execute("CREATE USER ituser3 WITH PASSWORD 'Initial1!'");
        engine.execute("ALTER USER ituser3 WITH PASSWORD 'Updated2!'");

        // Credential should still exist (mechanism unchanged)
        var desc = describeUser("ituser3");
        assertNotNull(desc, "Credential should still exist after ALTER USER");
        assertEquals(1, desc.credentialInfos().size());
        assertEquals(ScramMechanism.SCRAM_SHA_256, desc.credentialInfos().get(0).mechanism());
    }

    // ====================================================================
    // Test 4: DROP USER removes the credential
    // ====================================================================

    @Test
    @Order(4)
    void dropUser_removesCredential() throws Exception {
        engine.execute("CREATE USER ituser4 WITH PASSWORD 'Temp456!'");
        assertNotNull(describeUser("ituser4"), "Precondition: credential should exist before DROP");

        engine.execute("DROP USER ituser4");

        // After drop the credential store should be empty or the user should be gone
        boolean credentialsGone = credentialsRemovedFor("ituser4");
        assertTrue(credentialsGone, "Credentials should be removed after DROP USER");
    }

    // ====================================================================
    // Test 5: DROP USER is a no-op for a non-existent user
    // ====================================================================

    @Test
    @Order(5)
    void dropUser_nonExistent_doesNotThrow() {
        assertDoesNotThrow(() -> engine.execute("DROP USER ituser_ghost"));
    }

    // ====================================================================
    // Test 6: CREATE USER with optional WITH keyword is allowed
    // ====================================================================

    @Test
    @Order(6)
    void createUser_withoutWithKeyword_storesCredential() throws Exception {
        engine.execute("CREATE USER ituser6 PASSWORD 'Password6!'");

        var desc = describeUser("ituser6");
        assertNotNull(desc, "Credential should exist when WITH is omitted");
        assertEquals(ScramMechanism.SCRAM_SHA_256, desc.credentialInfos().get(0).mechanism());
    }

    // ====================================================================
    // Test 7: ALTER USER with optional WITH keyword is allowed
    // ====================================================================

    @Test
    @Order(7)
    void alterUser_withoutWithKeyword_updatesCredential() throws Exception {
        engine.execute("CREATE USER ituser7 WITH PASSWORD 'InitPass7!'");
        engine.execute("ALTER USER ituser7 PASSWORD 'NewPass7!'");

        var desc = describeUser("ituser7");
        assertNotNull(desc, "Credential should still exist after alter without WITH keyword");
        assertEquals(ScramMechanism.SCRAM_SHA_256, desc.credentialInfos().get(0).mechanism());
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    /**
     * Returns the credential description for the given username, or {@code null}
     * if the user does not exist or has no SCRAM credentials.
     */
    private UserScramCredentialsDescription describeUser(String username) {
        try {
            Map<String, UserScramCredentialsDescription> result =
                adminClient.describeUserScramCredentials(List.of(username)).all().get();
            UserScramCredentialsDescription desc = result.get(username);
            if (desc == null || desc.credentialInfos().isEmpty()) return null;
            return desc;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns {@code true} if the user has no SCRAM credentials (either the
     * user does not exist or every credential was deleted).
     */
    private boolean credentialsRemovedFor(String username) {
        return describeUser(username) == null;
    }
}
