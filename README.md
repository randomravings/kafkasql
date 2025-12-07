# KAFKA SQL

The idea behind this project is to create a structured query language for Kafka that can be used to do IO and resource management.
> DISCLAIMIER: This is currently just a side project to demonstrate an idea with code that might become intersting. This is very much subject to change, but if you find it intersting, I am very intersted to hear from you.

While there exists tools out there that enable you to interact with Kafka such as KSQL, Flink, and others - these are additions to, and run along side Kafka as opposed to be native to Kafka. What is more is that they are indended for Stream Processing which is in our case a secondary stage. What we are looking to do is to focus on more basic operations primarily:

- `CREATE`, `ALTER`, `DROP` for resource management.
- `SHOW`, `EXPLAIN` for explioing resources.
- `READ`, `WRITE` for IO operations.
- `SET`, `USE` for session management.

We use `READ` and `WRITE` since Kafka we consider it more appropriate for a streaming platform rather than `SELECT` and `INSERT`. The consideration is whether we want to allow for `DELETE` and `TRUNCATE` or leave it to the retention policy.

The resources we want to work on are:

- `CONTEX`: Functions like a namespace and will manifest physically as topic prefix.
- `STREAM`: This a topic and prefixed with the context it is created in. The statement will be accompanties with a schema and distribution/partition field list.
- `TYPE`: This is equialent to a schema for a reusable type that can reference each other or used by a stream.
- `USER`: For `authn` operations
- `ACL`: For `authz` operations

## Intended use

The Kafka SQL language is indended to be use *interactively*, which SQL languages shine, but also be used as a *command line* that additionally supports code generation to support DevOps pipelines, IDEs, or general offline activities. This is also why we will have an `INCLUCDE` pragma statemnt that is only used in CLI mode to allow us to split statements into multiple files to support Data as Code practices.

## Long term view

The inital idea is to create a client wrapper (Admin, Producer, and Consumer) to make it practical. The next challenge then is how to store the metadata.

The other long term idea is if it gains enough traction is to build it into the broker software so that server side validation, filtering, and even field level security can become possible.

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

Should output something like (like because this is changing every now and then ...):

```bash
Usage:
  kafkasql [-a] [-n] (-f <f1.kafka>[,<f2.kafka>...] ...)
  kafkasql [-a] -t <script...>
Options:
  -w, --working-dir   Base directory for includes (default: .)
  -f, --files         Comma separated list (can repeat)
  -t, --text          Inline script (consumes all remaining args)
  -n, --no-include    Disable INCLUDE resolution
  -a, --print-ast     Print AST if parse succeeds
  -v, --verbose       Enable antlr trace output
  -h, --help          Show this help
```

You can now run a short text and parse the syntax tree like so:

```bash
./kafkasql -a --text '
CREATE CONTEXT com;
USE CONTEXT com;
CREATE SCALAR Email AS STRING;
'
```

This highlights the fact that the context is a first class concept in Kafka SQL and is the only construct that can exist in the `root` context, or empty domain. All other constructs *must* belong to a named parent context. The `USE CONTEXT` sets the current context, but it has to be created first using the `CREATE CONTEX` statement which creates a context under the currently active context.

## Example Statements

Here are some currently statements to showcase the current state, note these are only parsed texts, and no execution is currently possible.

### Contexts

Contexts are inpsired by the idea of Domain-Driven Design, hence the name, and define boundaries, but can also be nested. Any additional Contexts, Types, or Streams will inherit the currently active context.

```SQL
CREATE CONTEXT com;         -- Creates a top level context
USE CONTEXT com;            -- Sets current context to 'com'

CREATE CONTEXT example;     -- Createst the context 'com.example'
USE CONTEXT example;        -- Sets current context to 'com.example'

USE CONTEXT .com            -- Set the current context to 'com' since '.' means root.
```

Thoughs here could be to add some permission sets or rules that are inherited.

### Scalars

Scalars are intended for strongly typed primitive types that can have validation and default value.

```SQL
-- Creates a strongly typed email as string
CREATE SCALAR Email AS STRING;

-- Creates a strongly typed ph value as an 8 bit integer
-- valid range is 0 (acidic) to 14 (base) inclusive and defaults to 7 (neutral) 
CREATE SCALAR PhValue AS INT8 CHECK (value BETWEEN 0 AND 14) DEFAULT 7;
```

### Enums

Enums are fairly self explanatory.

```SQL
-- Standard integer enum.
CREATE ENUM Ccy (
    EUR: 0,
    USD: 1,
    CNY: 2
);
```

But we are adding some flexibility to how we define them.

```SQL
-- Create an Enum by a 16 bit integer with default symbol B
CREATE Enum MoreStuff AS INT16 (
  A: 0,
  B: 1,
  C: 2
)
DEFAULT B;
```

### Structs

This is where it gets more interesting, as there are a lot of considerations to take care of, but here are some examples:

```SQL
CREATE STRUCT Person (
    Name STRING,
    DateOfBirth DATE OPTIONAL
);
```

Here is a more complete example that shows includes in action. This should look familiar to people with C, Protobuf, or Avro IDL experience.

```SQL
INCLUDE 'com/example/Email.kafka'

CREATE STRUCT User (
    Id INT32,
    Name String,
    Email com.example.Email OPTIONAL,
    Score DEFAULT 0.0
);
```

Here is an example with complex type defaults.

```SQL
-- Showcasing Default litterals for complext types.
CREATE STRUCT ComplexDefaults (
  ScalarDefault com.example.IntScalar DEFAULT 10,               -- Same as primitives.
  EnumDefault com.example.SomeEnum DEFAULT SomeEnum::Symbol,    -- Static access.
  UnionDefault com.example.SomeUnion DEFAULT StringField$'ABC', -- Dollar separating an identifier and literal.
  StructDefault com.example.SomeStruct DEFAULT {                -- JSON style but with Identifier for fields and literal values.
    Id: 1001,
    Name: 'John',
    Address: {
      Street: 'Far away street',
      Zip: 'pick one'
    }
  }
);
```

Next thing is to consider adding check constraints and complete the default to full JSON to allow for complex types instantiation.

### Unions

Unions will follow a C style unions.

```SQL
CREATE UNION ApplesOrOrange (
    apple com.example.Apple,
    orange com.example.Orange
);
```

Again we can debate whether to inclide a default or to defer it to the field in which is used.

### Streams

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
CREATE STREAM Users
TYPE (
    Id INT32,
    Name String,
    Email com.example.Email OPTIONAL,
    Score DEFAULT 0.0
) AS User
DISTRIBUTE BY (Id);
```

Here is a basic reference stream definition (many topics one schema)

```SQL
-- Basic referencing (many streams one schema)
INCLUDE 'com/example/User.kafka';

CREATE STREAM Users
TYPE com.example.User AS User
DISTRIBUTE BY (Id);
```

```SQL
-- Stream with atwon inline types (one stream many schemas)
INCLUDE 'com/example/User.kafka';

CREATE STREAM Users
TYPE (
    Id INT32,
    Email com.example.Email,
    Score OPTIONAL DEFAULT 0.0
) AS User
DISTRIBUTE BY (Id);
TYPE (
  Id INT32,
  Email com.example.Email OPTIONAL,
); -- Distribution omitted, not recommended, but hey ...
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
