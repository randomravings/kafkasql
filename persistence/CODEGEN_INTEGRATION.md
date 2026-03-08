# Persistence Module - Code Generation Integration

This module demonstrates "dogfooding" - using KafkaSQL to define its own persistence model.

## Architecture

The persistence event schema is defined in KafkaSQL and generates Java code at build time:

```
persistence/src/main/kafkasql/sys/persistence/events.kafka
    ↓ (build-time code generation)
persistence/build/generated/sources/kafkasql/main/sys/persistence/
    ├── EventType.java (enum)
    └── SymbolEventLog.java (sealed interface with SymbolEvent record)
```

## Generated Types

**EventType** (Enum):
```java
public enum EventType {
    NOOP(0),
    CREATE_STMT(1),
    ALTER_STMT(2),
    DROP_STMT(3)
}
```

**SymbolEventLog** (Sealed Interface + Record):
```java
public sealed interface SymbolEventLog permits SymbolEventLog.SymbolEvent {
    record SymbolEvent(
        UUID EventId,
        LocalDateTime EventTimestamp,
        String Source,
        sys.persistence.EventType EventType,
        String ObjectName,
        int ObjectVersion,
        String Delta,
        String State
    ) implements SymbolEventLog {}
}
```

## Integration Points

### 1. Build Integration

The `build.gradle` defines a code generation task that runs before compilation:

```gradle
tasks.register('generateKafkaSqlCode', JavaExec) {
    classpath = project(':codegen').sourceSets.main.runtimeClasspath
    mainClass = 'kafkasql.codegen.CodeGenerator'
    args kafkasqlSchemaFile, generatedSourceDir
}

tasks.named('compileJava') {
    dependsOn 'generateKafkaSqlCode'
}
```

### 2. EventAdapter Bridge

`EventAdapter.java` bridges the publisher API and generated types:

- **Publisher API**: Uses familiar Java types (String, long, Name)
- **Generated Types**: Uses schema-defined types (UUID, LocalDateTime, EventType enum)
- **Adapter**: Translates between the two, keeping API stable while allowing schema evolution

### 3. Serialization

Jackson serializers work with generated types:
- `EventSerializer` → Serializes `SymbolEvent` to JSON
- `EventDeserializer` → Deserializes JSON to `SymbolEvent`
- Includes JavaTimeModule for LocalDateTime/UUID support

## Usage

### Publishing Events

```java
var publisher = new KafkaEventPublisher("localhost:9092", "kafkasql-events");

// Create event
publisher.publishCreate(
    objectName,
    decl,
    stmt,
    "CREATE TYPE MyType AS SCALAR INT32;",
    "example.kafka",
    1  // version
);

// Alter event
publisher.publishAlter(
    objectName,
    decl,
    stmt,
    "ALTER TYPE MyType ADD CONSTRAINT ...;",
    "example.kafka",
    2,  // new version
    1   // previous version
);
```

### Consuming Events

```java
// Events come as SymbolEventLog.SymbolEvent
SymbolEventLog.SymbolEvent event = deserialize(bytes);

// Pattern match on sealed interface (if needed in future for multiple types)
String objectName = event.ObjectName();
EventType eventType = event.EventType();
int version = event.ObjectVersion();
```

## Benefits

1. **Single Source of Truth**: Schema defined once in KafkaSQL
2. **Type Safety**: Compiler enforces correct usage of generated types
3. **Schema Evolution**: Change `.kafka` file, rebuild, get new Java types
4. **Dogfooding**: We use our own language to define our data structures
5. **Clean Separation**: API layer (publisher) decoupled from wire format (generated types)

## Future Enhancements

- [ ] Implement Jackson serialization for AST nodes (Stmt, Decl)
- [ ] Add query API for reading events
- [ ] Implement auto-versioning (query latest version before publish)
- [ ] Add schema registry integration
- [ ] Support multiple message types in stream (currently only SymbolEvent)
