# KAFKA SQL

The idea behind this project is to create a structured query language for Kafka that that is native to Apache Kafka and can be used to do IO and resource management.

While there exists tools out there that enable you to interact with Kafka such as KSQL, Flink, and others - these are additions to, and run along side Kafka as opposed to be native to Kafka. What is more is that they are intended for stream processing use cases, which is to say they are slanted towards the Data Engineering side of things. This project focuses on the more genereric Sofware Engineering world with dumb pipes and smart endpoints. While we want to base it on standard SQL syntax, this particular flavor will be targetting on STREAMS, READ and WRITE.

The project will go into three phases:

1. Defining the grammaer and the ability to mock up how it is intended to be used.
2. Plugging it with Kafka using purely Client Libraries to make it more real.
3. Test it out on a Fork of Apache Kafka so it ships naturally and provide server side valdidation.

Here is taste of what is currently targetted for the syntax.

- `CREATE`, `ALTER`, `DROP` for resource management.
- `SHOW`, `EXPLAIN` for explioing resources.
- `READ`, `WRITE` for IO operations.
- `SET`, `USE` for session management.

We use `READ` and `WRITE` since Kafka we consider it more appropriate for a streaming platform rather than `SELECT` and `INSERT`. The consideration is whether we want to allow for `DELETE` and `TRUNCATE` or leave it to the retention policy.

The resources we wan

- `CONTEX`: Functions like a namespace and will manifest physically as topic prefix.
- `STREAM`: This backed by a topic and prefixed with the context it is created in. The statement will be accompanties with a schema and distribution/partition field list.
- `TYPE`: This is equialent to a schema for a reusable type that can reference each other or used by a stream.
- `USER`: For `authn` operations
- `ACL`: For `authz` operations
- `INCLUDE`: To enable splitting up large files or just order definitions into a project structure.

## Testing it

The code was built using Java 21 and Gradle 9.
If you want to test it feel free to clone the repository, and then do the folliwing.

First create the gradle wrapper:

```bash
gradle wrapper
```

Then create the shadow jar:

```bash
./gradlew clean build
```

The enables the usage of the kafkasql CLI using the bash script:

```bash
./kafkasql -h
```

You can now run a short text and show the syntax tree like so:

```bash
./kafkasql -a --text '
CREATE CONTEXT com;
USE CONTEXT com;
CREATE TYPE Email AS SCALAR STRING;
'
```

This highlights the fact that the context is a first class concept in Kafka SQL and is the only construct that can exist in the `root` context, or empty domain. All other constructs *must* belong to a named parent context. The `USE CONTEXT` sets the current context, but it has to be created first using the `CREATE CONTEX` statement which creates a context under the currently active context. All `CREATE` statements have an identifier as name, but their full name depends on what context is active when created.

## Contexts

Contexts are inpsired by the idea of Domain-Driven Design, hence the name, and define boundaries, but can also be nested. Any additional Contexts, Types, or Streams will inherit the currently active context.

```SQL
/*
 * A multi line comment
 *
 * Just cuz'
 */
CREATE CONTEXT com;         -- Creates a top level context
USE CONTEXT com;            -- Sets current context to 'com'

CREATE CONTEXT example;
COMMENT 'This is the com.example context';

USE CONTEXT com.example;    -- Sets current context to 'com.example'
```

This is how to create nested contexts, currentl we can only set commends, but an idea could be to add some security descriptors as well.

## Scalars

Scalars are intended for strongly typed primitive types that can have validation and default value.

```SQL
/*
 * Makes com.example avalaible for reference.
 */
INCLUDE 'com/example.kafka'

USE CONTEXT com.example; 

-- Creates a strongly typed email as string
CREATE TYPE Email AS EMAIL STRING;

-- Creates a strongly typed ph value as an 8 bit integer
CREATE TYPE PhValue AS SCALAR INT8
CHECK (value BETWEEN 0 AND 14)
DEFAULT 7
COMMENT 'Value for PH value of a liquid
Ranges are:
- 0  Base (min)
- 7  Neutral (default)
- 14 Acidic (max)'
```

### Enums

```SQL
INCLUDE 'com/example.kafka'

USE CONTEXT com.example; 

CREATE TYPE ENUM AS Ccy (
    EUR = 0,
    USD = 1,
    CNY = 1 << 1
    COMMENT 'Members can have const expressions too'
);
```

```SQL
INCLUDE 'com/example.kafka'

USE CONTEXT com.example; 

-- Create an Enum by a 16 bit integer with default symbol B
CREATE TYPE MoreStuff AS ENUM INT16 (
  A = 0,
  B = 1,
  C = 2
)
DEFAULT MoreStuff::B
COMMENT 'Enum litterlas use :: similar to static access of members'
```

### Structs

```SQL
INCLUDE 'com/example.kafka'

USE CONTEXT com.example; 

CREATE TYPE Person AS STRUCT (
    Name STRING,
    DateOfBirth DATE NULL
    COMMENT 'Date of birth is nullable or optional'
);
```

```SQL
INCLUDE 'com/example.kafka'
INCLUDE 'com/example/Email.kafka' -- makes com.example.Email reference avaialble

USE CONTEXT com.example;

CREATE TYPE User AS STRUCT (
    Id INT32,
    Name String,
    Email com.example.Email NULL,
    Score DECIMAL(5, 2) DEFAULT 0.0
);
```

```SQL
-- include stuff ...

CREATE TYPE ComplexDefaults AS STRUCT (
  ScalarDefault com.example.IntScalar DEFAULT 10,               -- Same as primitives.
  EnumDefault com.example.SomeEnum DEFAULT SomeEnum::Symbol,    -- Static access.
  UnionDefault com.example.SomeUnion DEFAULT StringField$'ABC', -- Dollar separating an identifier and literal.
  StructDefault com.example.SomeStruct
  COMMENT 'JSON style default but with @ to disabiguate from map defualt
           and Identifier for fields and literal values.'
  DEFAULT @{
    Id: 1001,
    Name: 'John',
    Address: {
      Street: 'Far away street',
      Zip: 'pick one'
    }
  }
);
```

## Unions

```SQL
CREATE TYPE ApplesOrOrange AS UNION(
    apple com.example.Apple,
    orange com.example.Orange
);
```

## Streams

Now for the streams them selves and they take a bit more explaining, and it has mostly to do with the structure of streams. Essentially there are three patterns which are:

- One Topic -> one Schema, which is the most common case and similar to create table
- Many topics -> one Schema, which is about reducing redundancy when it is the case that multiple topics have the same structure.
- One topic -> many Schemmas, which is when different types have to be handled in order, also to reduce used partition count.
- Many topics -> many Schemas, imagine a combination of the above.

The first three can be covered when using [Confluent Schema Registry](https://docs.confluent.io/platform/current/clients/confluent-kafka-dotnet/_site/api/Confluent.SchemaRegistry.SubjectNameStrategy.html). But the way we do it, it is possible to add the fourth, and then we can debate whether it is a good idea.

There is no notion of *key* and *value* in the syntax, we are only working with values per se, but we can dictate the partitioning by specifying a set of fields to partition by. The hash bytes computation will opague.

Every valid schema will start with a `TYPE` keyword and then followed by either list of fidlds similar `STRUCT`, or a reference that must resolve to a `STRUCT`. Every type must have a *unique* alias which is used for referencing which type is being referenced in queries, and *optionally* a `DISTRIBUTE BY` clause to control paritioning. A stream can have any number of `TYPE`s specified.

```SQL
-- Basic inline stream with distribution (one stream one schema).
CREATE STREAM Users (
  TYPE User AS(
      Id INT32,
      Name String,
      Email com.example.Email OPTIONAL,
      Score DEFAULT 0.0
  )
  DISTRIBUTE BY (Id)
);
```

Here is a basic reference stream definition (many topics one schema)

```SQL
-- Basic referencing (many streams one schema)
INCLUDE 'com/example/User.kafka';

CREATE STREAM Users
TYPE User AS com.example.User
DISTRIBUTE BY (Id);
```

```SQL
-- Stream with atwon inline types (one stream many schemas)
INCLUDE 'com/example/User.kafka';

CREATE STREAM Users (
  TYPE LegacyUser AS (
    Id INT32,
    Email com.example.Email OPTIONAL,
  ), -- Distribution omitted, not recommended, but hey ...
  TYPE User AS (
      Id INT32,
      RegistrationTs TIMESTAMP(3),
      Email com.example.Email,
      Score OPTIONAL DEFAULT 0.0
  )
  DISTRIBUTE BY (Id)
  TIMESTAMP BY (RegistrationTs) -- This field goes into the Record timestamp.
);
```

```SQL
-- Stream with an inline and a reference (many streams many schemas)
INCLUDE 'com/example/User.kafka';

CREATE STREAM Users
TYPE (
    Id INT32,
    Email com.example.Email,
    Score OPTIONAL DEFAULT 0.0
) AS UserA
DISTRIBUTE BY (Id)
TYPE com.example.User AS UserB
DISTRIBUTE BY (Id);
```
