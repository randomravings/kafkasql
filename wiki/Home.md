# KafkaSQL Wiki

Welcome to the KafkaSQL documentation. KafkaSQL is a type-safe schema definition language for Kafka with built-in support for validation, code generation, and IDE integration.

## Contents

- [Project Structure](Project-Structure.md) - Module organization and architecture
- [Language Syntax](Language-Syntax.md) - Complete syntax reference
- [Type System](Type-System.md) - Types, validation, and constraints
- [Command Line Interface](CLI.md) - Using the kafkasql compiler
- [Language Server Protocol](LSP.md) - IDE integration and VS Code extension
- [Pipeline Architecture](Pipeline-Architecture.md) - Compilation phases and extensibility

## Quick Start

```bash
# Compile a KafkaSQL file
./kafkasql -f myschema.kafka

# Run with include resolution
./kafkasql -w src/schemas -f main.kafka

# Lint only mode
./kafkasql -l -f myschema.kafka
```

## Example

```kafkasql
CREATE CONTEXT com.example;

CREATE TYPE UserId AS SCALAR INT32;

CREATE TYPE UserStatus AS ENUM (
    ACTIVE,
    INACTIVE,
    SUSPENDED
);

CREATE TYPE User AS STRUCT (
    Id UserId,
    Name STRING,
    Email STRING,
    Status UserStatus DEFAULT UserStatus::ACTIVE
);

CREATE STREAM UserEvents (
    Key UserId,
    Value User
);
```
