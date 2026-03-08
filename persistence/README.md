# KafkaSQL Persistence Module

The persistence module implements a CQRS (Command Query Responsibility Segregation) event sourcing system for persisting the KafkaSQL semantic model to Apache Kafka.

## Architecture

### Event Log Pattern

Instead of storing only the current state, this module stores **every change** (CREATE/ALTER/DROP) as an immutable event in a Kafka topic. This provides:

- **Complete audit trail**: Every change is recorded with timestamp, source file, and statement
- **Time travel**: Query the state at any point in history
- **Event replay**: Rebuild read models from the event stream
- **Distributed consistency**: Kafka's ordering guarantees ensure consistent views

### CQRS Design

```
┌─────────────┐
│   Compiler  │ (Write Side - Command)
│  (Semantic) │
└──────┬──────┘
       │ publishes
       ▼
┌─────────────────┐
│  Kafka Topic    │ (Event Store)
│ kafkasql-events │
└────────┬────────┘
         │ consumes
         ▼
┌─────────────────┐
│  Read Models    │ (Query Side)
│ - Symbol Table  │
│ - Search Index  │
│ - Dependencies  │
└─────────────────┘
```

**Write Side (Command)**: The compiler validates statements and publishes events.

**Event Store**: Kafka topic with delete retention (NOT compacted) - keeps all events forever.

**Read Side (Query)**: Consumers materialize different views optimized for specific queries.

## Event Model

### SymbolTableEvent

Core event record capturing all changes:

```java
record SymbolTableEvent(
    String eventId,          // UUID for deduplication
    long timestamp,          // When event occurred
    String source,           // Source file (e.g., "example.kafka")
    EventType type,          // CREATE, ALTER, DROP
    Stmt statement,          // The AST statement
    String statementText,    // Original source (optional)
    Name objectName,         // FQN of the object
    long version,            // Version number (1-based)
    Long previousVersion,    // Previous version (null for CREATE)
    Decl finalState          // Complete object state (null for DROP)
)
```

### Versioning Semantics

- Each object has a monotonically increasing version counter
- CREATE starts at version 1
- Each ALTER increments the version
- DROP marks the object as deleted but retains history
- Version `-1` (LATEST_VERSION constant) is reserved for queries

### Event Types

**CREATE**: New object creation
- `previousVersion`: must be null
- `finalState`: complete object definition
- `version`: typically 1 (unless explicit)

**ALTER**: Object modification
- `previousVersion`: previous version number
- `finalState`: complete updated definition (not a delta)
- `version`: must be > previousVersion

**DROP**: Object deletion
- `previousVersion`: last valid version
- `finalState`: must be null
- `version`: must be > previousVersion

## Publisher Interface

### SymbolTableEventPublisher

Main interface for publishing events with two overloads per operation:

```java
// Explicit version (full control)
void publishCreate(Name objectName, Decl decl, Stmt statement, 
                  String statementText, String source, long version)

// Auto-version (convenience - uses version 1)
void publishCreate(Name objectName, Decl decl, Stmt statement,
                  String statementText, String source)
```

Similar overloads exist for `publishAlter` and `publishDrop`.

### KafkaEventPublisher

Kafka-based implementation with strong guarantees:

```java
var publisher = new KafkaEventPublisher(
    "localhost:9092",           // Bootstrap servers
    "kafkasql-events"           // Topic name
);

// Publish a CREATE event
publisher.publishCreate(
    objectName,
    decl,
    statement,
    statementText,
    "example.kafka",
    1  // version
);

publisher.flush();  // Ensure all events committed
publisher.close();
```

**Configuration**:
- `acks=all`: Wait for all replicas
- `enable.idempotence=true`: Exactly-once semantics
- `max.in.flight.requests.per.connection=1`: Strict ordering
- Partitioning: By `objectName.fullName()` for ordering guarantees

## Serialization

Jackson-based JSON serialization:

- **EventSerializer**: Converts `SymbolTableEvent` → JSON bytes
- **EventDeserializer**: Converts JSON bytes → `SymbolTableEvent`

Events are pretty-printed JSON for human readability and debugging.

## Kafka Topic Setup

The event topic should be configured with:

```bash
# Create topic with delete retention (NOT compacted)
kafka-topics.sh --create \
  --topic kafkasql-events \
  --partitions 10 \
  --replication-factor 3 \
  --config retention.ms=-1 \
  --config cleanup.policy=delete
```

**Key Configuration**:
- `cleanup.policy=delete`: Keep all events (not compacted)
- `retention.ms=-1`: Retain forever (or set appropriate retention)
- Partitions: Based on expected object count and throughput
- Replication: For durability (typically 3)

## Integration Points

### Compiler Integration

After successful semantic analysis:

```java
public class SemanticPhase {
    private SymbolTableEventPublisher publisher;
    
    public void process(Decl decl, String source) {
        // Validate and register in symbol table
        Name name = resolveName(decl);
        if (symbolTable.register(name, decl)) {
            // Publish CREATE event
            publisher.publishCreate(
                name,
                decl,
                stmt,
                sourceText,
                source
            );
        }
    }
}
```

### Query Side (Future)

Consumers can materialize read models:

```java
// Symbol table consumer
consumer.subscribe("kafkasql-events");
for (var record : consumer.poll(Duration.ofSeconds(1))) {
    SymbolTableEvent event = record.value();
    switch (event.type()) {
        case CREATE -> symbolTable.put(event.objectName(), event.finalState());
        case ALTER -> symbolTable.put(event.objectName(), event.finalState());
        case DROP -> symbolTable.remove(event.objectName());
    }
}
```

## Current Limitations

**Auto-versioning not implemented**: The `publishAlter` and `publishDrop` overloads without explicit version throw `UnsupportedOperationException`. These require querying the latest version from Kafka, which needs a separate query API.

**AST Serialization**: The `Stmt` and `Decl` AST nodes may not serialize cleanly to JSON yet. May need custom Jackson serializers/deserializers.

**No Read API**: Currently only write side (publishing). Read side (querying events, building projections) is future work.

## Dependencies

- Kafka Clients 4.0.0
- Jackson 2.18.2 (JSON serialization)
- SLF4J (logging)
- Depends on: `:runtime` (Name), `:lang` (Stmt, Decl)

## Next Steps

1. **Custom AST Serialization**: Implement Jackson modules for `Stmt` and `Decl` serialization
2. **Query API**: Implement `SymbolTableQuery` for reading events and building projections
3. **Auto-versioning**: Implement version lookup for auto-version overloads
4. **Testing**: Unit tests for events, integration tests with embedded Kafka
5. **Semantic Integration**: Wire up `SemanticPhase` to publish events
6. **Topic Admin**: Utilities for creating/managing the Kafka topic
7. **Consumer Framework**: Base classes for read model consumers
8. **Snapshots**: For large symbol tables, implement periodic state snapshots

## Example

Complete example of creating and publishing an event:

```java
// Setup
var publisher = new KafkaEventPublisher("localhost:9092", "kafkasql-events");

// After semantic validation in compiler
Name name = Name.of("com.example", "UserType");
TypeDecl decl = /* parsed and validated TypeDecl */;
CreateStmt stmt = /* the CREATE STRUCT statement */;
String source = "schema.kafka";

// Publish CREATE event
publisher.publishCreate(name, decl, stmt, null, source, 1);

// Ensure committed
publisher.flush();
publisher.close();
```

The event will be stored in Kafka as:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1234567890000,
  "source": "schema.kafka",
  "type": "CREATE",
  "statement": { /* Stmt AST */ },
  "statementText": null,
  "objectName": "com.example.UserType",
  "version": 1,
  "previousVersion": null,
  "finalState": { /* TypeDecl AST */ }
}
```
