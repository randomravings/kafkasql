# Language Syntax

KafkaSQL is a type-safe schema definition language designed for Kafka with a focus on readability and maintainability.

## Language Characteristics

- **Case-insensitive keywords and identifiers** (but linter enforces conventions)
- **Context-based namespacing** for organizing types
- **Strong type system** with validation
- **Include system** for modular schemas

## File Extension

`.kafka`

## Comments

```kafkasql
// Single-line comment

/* Multi-line
   comment */
```

## Statements

### INCLUDE

Import definitions from other files. Paths are relative to the working directory.

```kafkasql
INCLUDE 'com/example/common.kafka';
INCLUDE 'shared/types.kafka';
```

**Features:**

- Dependency resolution with cycle detection
- Precise error reporting for missing files
- Working directory configurable via CLI (`-w` flag)

### USE CONTEXT

Set the current namespace context for subsequent declarations.

```kafkasql
USE CONTEXT com.example;

// Types created here will be in com.example namespace
CREATE TYPE UserId AS SCALAR INT32;  // → com.example.UserId
```

### CREATE CONTEXT

Declare a new namespace context.

```kafkasql
CREATE CONTEXT com.example;
CREATE CONTEXT com.example.nested;
```

**Contexts:**

- Organize types hierarchically
- Support nested contexts with dot notation
- Can be absolute or relative

## Type Declarations

### SCALAR Types

Define a new scalar type based on a primitive.

```kafkasql
CREATE TYPE UserId AS SCALAR INT32;
CREATE TYPE Price AS SCALAR DOUBLE;
CREATE TYPE Name AS SCALAR STRING;
```

**With Default Value:**

```kafkasql
CREATE TYPE Counter AS SCALAR INT32 DEFAULT 0;
```

**With CHECK Constraint:**

```kafkasql
CREATE TYPE Age AS SCALAR INT32 
    CHECK Age >= 0 AND Age <= 150;

CREATE TYPE Percentage AS SCALAR DOUBLE 
    CHECK Percentage >= 0.0 AND Percentage <= 100.0;
```

**Primitive Types:**

- `BOOLEAN`
- `INT8`, `INT16`, `INT32`, `INT64`
- `FLOAT32`, `FLOAT64`, `DECIMAL(p, s)`
- `STRING`, `STRING(n)`
- `BYTES`, `BYTES(n)`
- `DATE`
- `TIME(p)`, `TIMESTAMP(p)`, `TIMESTAMP_TZ(p)`

### ENUM Types

Define an enumeration with named symbols.

```kafkasql
CREATE TYPE Status AS ENUM (
    ACTIVE,
    INACTIVE,
    SUSPENDED
);
```

**With Base Type and Default:**

```kafkasql
CREATE TYPE Priority AS ENUM INT8 (
    LOW = 0,
    MEDIUM = 1,
    HIGH = 2
) DEFAULT Priority::MEDIUM;
```

**Enumerators:**

- Can have explicit integer values
- Support any integer base type
- Default value uses `::` syntax

**Linting:** Enum symbols should be SCREAMING_SNAKE_CASE

### STRUCT Types

Define a composite type with named fields.

```kafkasql
CREATE TYPE User AS STRUCT (
    Id UserId,
    Name STRING,
    Email STRING,
    Age INT32
);
```

**With Defaults:**

```kafkasql
CREATE TYPE Settings AS STRUCT (
    Theme STRING DEFAULT "dark",
    Notifications BOOLEAN DEFAULT true,
    MaxItems INT32 DEFAULT 100
);
```

**With CHECK Constraints:**

```kafkasql
CREATE TYPE Product AS STRUCT (
    Name STRING,
    Price DOUBLE,
    Quantity INT32,
    CONSTRAINT valid_price CHECK Price > 0,
    CONSTRAINT valid_quantity CHECK Quantity >= 0
);
```

**Field Features:**

- Type references (qualified or unqualified)
- Optional default values
- Multiple named CHECK constraints per struct

**Linting:** Field names should be PascalCase

### UNION Types

Define a discriminated union with named alternatives.

```kafkasql
CREATE TYPE Result AS UNION (
    Success STRING,
    Error ErrorCode
);
```

**Nested Unions:**

```kafkasql
CREATE TYPE Value AS UNION (
    IntValue INT32,
    StringValue STRING,
    NestedValue Result
);
```

**Union Literals:**

Use `$` to create union instances:

```kafkasql
CREATE TYPE Container AS STRUCT (
    Data Value DEFAULT Value$IntValue(42),
    Status Result DEFAULT Result$Success("OK")
);
```

**Member Naming:**

- Each alternative has a name and a type
- Access via `UnionType$MemberName(value)` syntax

**Linting:** Member references should use exact case from definition

### STREAM Types

Define Kafka stream schemas with key and value types.

```kafkasql
CREATE STREAM UserEvents (
    Key UserId,
    Value User
);
```

**With Inline Definitions:**

```kafkasql
CREATE STREAM LogEvents (
    Key STRING,
    Value STRUCT (
        Timestamp INT64,
        Level STRING,
        Message STRING
    )
);
```

**Compact Form:**

```kafkasql
CREATE STREAM Notifications KEY UserId VALUE STRUCT (
    Type STRING,
    Content STRING,
    Timestamp INT64
);
```

## Type References

### Qualified Names

```kafkasql
com.example.UserId
com.example.nested.Type
```

### Unqualified Names

```kafkasql
UserId  // Resolved in current context
```

**Resolution:**

- Searches current context first
- Falls back to parent contexts
- Case-insensitive lookup

## Literals

### Scalar Literals

```kafkasql
42              // INT32
42L             // INT64
3.14            // DOUBLE
3.14f           // FLOAT
"hello"         // STRING
true, false     // BOOLEAN
```

### Enum Literals

```kafkasql
Status::ACTIVE
Priority::HIGH
```

Use `::` to reference enum symbols.

### Union Literals

```kafkasql
Result$Success("completed")
Result$Error(ErrorCode::NOT_FOUND)
Value$IntValue(100)
```

Use `$` to specify which union alternative.

## Expressions

### Supported in CHECK Constraints

```kafkasql
CHECK Age >= 18
CHECK Price > 0 AND Price < 1000
CHECK Length >= MinLength AND Length <= MaxLength
CHECK Status = Status::ACTIVE OR Status = Status::PENDING
```

**Operators:**

- Comparison: `=`, `!=`, `<`, `<=`, `>`, `>=`
- Logical: `AND`, `OR`, `NOT`
- Arithmetic: `+`, `-`, `*`, `/`, `%`

**Operands:**

- Field references
- Literals
- Enum symbols

## Naming Conventions (Enforced by Linter)

| Element | Convention | Example |
|---------|-----------|---------|
| Types | PascalCase | `UserId`, `UserStatus` |
| Fields | PascalCase | `FirstName`, `EmailAddress` |
| Enum Symbols | SCREAMING_SNAKE_CASE | `ACTIVE`, `NOT_FOUND` |
| Contexts | lowercase.dot.notation | `com.example`, `myapp` |

## Complete Example

```kafkasql
// common.kafka
CREATE CONTEXT com.example;

CREATE TYPE UserId AS SCALAR INT64;

CREATE TYPE UserStatus AS ENUM (
    ACTIVE,
    INACTIVE,
    SUSPENDED
) DEFAULT UserStatus::ACTIVE;

// user.kafka
INCLUDE 'common.kafka';

USE CONTEXT com.example;

CREATE TYPE Email AS SCALAR STRING
    CHECK LENGTH(Email) > 0;

CREATE TYPE User AS STRUCT (
    Id UserId,
    Name STRING,
    Email Email,
    Age INT32,
    Status UserStatus DEFAULT UserStatus::ACTIVE,
    CONSTRAINT valid_age CHECK Age >= 0 AND Age <= 150,
    CONSTRAINT valid_name CHECK LENGTH(Name) > 0
);

CREATE STREAM UserEvents (
    Key UserId,
    Value User
);

CREATE TYPE UserAction AS UNION (
    Created User,
    Updated User,
    Deleted UserId
);

CREATE STREAM UserActions KEY UserId VALUE UserAction;
```

## Reserved Keywords

```bash
CREATE, TYPE, AS, SCALAR, ENUM, STRUCT, UNION, STREAM
CONTEXT, USE, INCLUDE
CHECK, CONSTRAINT, DEFAULT
KEY, VALUE
AND, OR, NOT
TRUE, FALSE
```

## Future Syntax (Planned)

- **Functions:** User-defined validation functions
- **Imports:** Selective imports instead of INCLUDE all
- **Type Parameters:** Generic types
- **Annotations:** Metadata for code generation
