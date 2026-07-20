# KafkaSQL Wiki

KafkaSQL documentation is organized for both first-time users and contributors.

## Start Here

- [Getting Started](getting-started/Home.md) - First run and onboarding

## Sections

- [Language](language/Home.md) - Syntax and statement reference
- [Architecture](architecture/Home.md) - Repository structure and pipeline design

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

## Navigation Tips

- Subsection pages include a navigation line with both `Up` and `Home` links.
- `Up` returns to the section index.
- `Home` returns to this page.
