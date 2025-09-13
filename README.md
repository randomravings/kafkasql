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

You can now run something like:

```bash
./kafkasql -a --text 'CREATE SCALAR Email AS String;'
```

Print the usage:

```bash
./kafkasql -h
```

which should output:

```bash
Usage:
  kafkasql [-a] [-n] (-f <f1.sqls>[,<f2.sqls>...] ...)
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

### Scalars (Type)

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
-- Create a bit mask backed by a 16 bit integer with a default flag of B
CREATE MASK Flags AS INT16 (
  A: 0,
  B: 1,
  C: 2
)
DEFAULT B;
```

The key thing to bear in mind is that the MASK is essentually an enum, but the values correspond to bit index rather than values. Defaults and backing integers are valid for either `ENUM` or `MASK`.

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
INCLUDE 'com/example/Email.sqls'

CREATE STRUCT User (
    Id INT32,
    Name String,
    Email com.example.Email OPTIONAL,
    Score DEFAULT 0.0
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
INCLUDE 'com/example/User.sqls';

CREATE STREAM Users
TYPE com.example.User AS User
DISTRIBUTE BY (Id);
```

```SQL
-- Stream with atwon inline types (one stream many schemas)
INCLUDE 'com/example/User.sqls';

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
INCLUDE 'com/example/User.sqls';

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

## Lexer (EBNF)

```EBNF
/* converted on Sat Sep 13, 2025, 17:59 (UTC+02) by antlr_4-to-w3c v0.73-SNAPSHOT which is Copyright (c) 2011-2025 by Gunther Rademacher <grd@gmx.net> */

_        ::= WS
           | COMMENT
           | WS_PATH
          /* ws: definition */

<?TOKENS?>

INCLUDE  ::= [Ii] [Nn] [Cc] [Ll] [Uu] [Dd] [Ee]
CREATE   ::= [Cc] [Rr] [Ee] [Aa] [Tt] [Ee]
CONTEXT  ::= [Cc] [Oo] [Nn] [Tt] [Ee] [Xx] [Tt]
USE      ::= [Uu] [Ss] [Ee]
SCALAR   ::= [Ss] [Cc] [Aa] [Ll] [Aa] [Rr]
ENUM     ::= [Ee] [Nn] [Uu] [Mm]
STRUCT   ::= [Ss] [Tt] [Rr] [Uu] [Cc] [Tt]
UNION    ::= [Uu] [Nn] [Ii] [Oo] [Nn]
STREAM   ::= [Ss] [Tt] [Rr] [Ee] [Aa] [Mm]
READ     ::= [Rr] [Ee] [Aa] [Dd]
WRITE    ::= [Ww] [Rr] [Ii] [Tt] [Ee]
FROM     ::= [Ff] [Rr] [Oo] [Mm]
TO       ::= [Tt] [Oo]
LOG      ::= [Ll] [Oo] [Gg]
COMPACT  ::= [Cc] [Oo] [Mm] [Pp] [Aa] [Cc] [Tt]
TYPE     ::= [Tt] [Yy] [Pp] [Ee]
WHERE    ::= [Ww] [Hh] [Ee] [Rr] [Ee]
AS       ::= [Aa] [Ss]
OPTIONAL ::= [Oo] [Pp] [Tt] [Ii] [Oo] [Nn] [Aa] [Ll]
VALUES   ::= [Vv] [Aa] [Ll] [Uu] [Ee] [Ss]
OR       ::= [Oo] [Rr]
AND      ::= [Aa] [Nn] [Dd]
XOR      ::= [Xx] [Oo] [Rr]
IS       ::= [Ii] [Ss]
NOT      ::= [Nn] [Oo] [Tt]
DEFAULT  ::= [Dd] [Ee] [Ff] [Aa] [Uu] [Ll] [Tt]
TRUE     ::= [Tt] [Rr] [Uu] [Ee]
FALSE    ::= [Ff] [Aa] [Ll] [Ss] [Ee]
NULL     ::= [Nn] [Uu] [Ll] [Ll]
BOOL     ::= [Bb] [Oo] [Oo] [Ll]
INT8     ::= [Ii] [Nn] [Tt] '8'
INT16    ::= [Ii] [Nn] [Tt] '16'
INT32    ::= [Ii] [Nn] [Tt] '32'
INT64    ::= [Ii] [Nn] [Tt] '64'
FLOAT32  ::= [Ff] [Ll] [Oo] [Tt] '32'
FLOAT64  ::= [Ff] [Ll] [Oo] [Tt] '64'
DECIMAL  ::= [Dd] [Ee] [Cc] [Ii] [Mm] [Aa] [Ll]
STRING   ::= [Ss] [Tt] [Rr] [Ii] [Nn] [Gg]
CHAR     ::= [Cc] [Hh] [Aa] [Rr]
BYTES    ::= [Bb] [Yy] [Tt] [Ee] [Ss]
FIXED    ::= [Ff] [Ii] [Xx] [Ee] [Dd]
UUID     ::= [Uu] [Uu] [Ii] [Dd]
DATE     ::= [Dd] [Aa] [Tt] [Ee]
TIME     ::= [Tt] [Ii] [Mm] [Ee]
TIMESTAMP
         ::= [Tt] [Ii] [Mm] [Ee] [Ss] [Tt] [Aa] [Mm] [Pp]
TIMESTAMP_TZ
         ::= [Tt] [Ii] [Mm] [Ee] [Ss] [Tt] [Aa] [Mm] [Pp] '_' [Tt] [Zz]
LIST     ::= [Ll] [Ii] [Ss] [Tt]
MAP      ::= [Mm] [Aa] [Pp]
DISTRIBUTE
         ::= [Dd] [Ii] [Ss] [Tt] [Rr] [Ii] [Bb] [Uu] [Tt] [Ee]
BY       ::= [Bb] [Yy]
BETWEEN  ::= [Bb] [Ee] [Tt] [Ww] [Ee] [Ee] [Nn]
IN       ::= [Ii] [Nn]
OF       ::= [Oo] [Ff]
MASK     ::= [Mm] [Aa] [Ss] [Kk]
CHECK    ::= [Cc] [Hh] [Ee] [Cc] [Kk]
INTEGER_LIT
         ::= '-'? [0-9]+
NUMBER_LIT
         ::= '-'? [0-9]+ '.' ( ( [eE] [+#x2D]? )? [0-9]+ )?
STRING_LIT
         ::= "'" ( [^'#xd#xa] | "''" )* "'"
BYTES_LIT
         ::= '0' [xX] [0-9a-fA-F]+
ID       ::= [a-zA-Z] [a-zA-Z0-9_]*
WS       ::= [ #x9#xd#xa]+
COMMENT  ::= '--' [^#xd#xa]*
WS_PATH  ::= [ #x9#xd#xa]+
FILE_PATH
         ::= "'" PATH_SEG ( '/' PATH_SEG )* '.' FILE_EXT "'"
PATH_SEG ::= [a-zA-Z0-9_#x2D]+
FILE_EXT ::= [Ss] [Qq] [Ll] [Ss]
```

## Parser (EBNF)

```EBNF
/* converted on Sat Sep 13, 2025, 18:01 (UTC+02) by antlr_3-to-w3c v0.73-SNAPSHOT which is Copyright (c) 2011-2025 by Gunther Rademacher <grd@gmx.net> */

script   ::= includeSection? ( statement SEMI )+ EOF
includeSection
         ::= ( includePragma SEMI )+
includePragma
         ::= INCLUDE filePath
filePath ::= FILE_PATH
statement
         ::= useStmt
           | readStmt
           | writeStmt
           | createContext
           | createScalar
           | createEnum
           | createStruct
           | createUnion
           | createStream
useStmt  ::= useContext
useContext
         ::= USE CONTEXT qname
readStmt ::= READ FROM streamName typeBlock+
streamName
         ::= qname
typeBlock
         ::= TYPE typeName projection whereClause?
whereClause
         ::= WHERE expr
writeStmt
         ::= WRITE TO streamName TYPE typeName LPAREN projection RPAREN VALUES tuple ( COMMA tuple )*
projection
         ::= STAR
           | accessor ( COMMA accessor )*
accessor ::= ( identifier | LBRACK literal RBRACK ) ( DOT ( identifier | LBRACK literal RBRACK ) )*
tuple    ::= LPAREN literal ( COMMA literal )* RPAREN
createContext
         ::= CREATE CONTEXT identifier
createScalar
         ::= CREATE SCALAR typeName AS primitiveType ( CHECK LPAREN expr RPAREN )? ( DEFAULT literal )?
createEnum
         ::= CREATE ENUM typeName ( OF enumType )? ( AS MASK )? LPAREN enumSymbol ( COMMA enumSymbol )* RPAREN ( DEFAULT identifier )?
enumType ::= INT8
           | INT16
           | INT32
           | INT64
enumSymbol
         ::= identifier COLON INTEGER_LIT
createStruct
         ::= CREATE STRUCT typeName LPAREN fieldDef ( COMMA fieldDef )* RPAREN
createUnion
         ::= CREATE UNION typeName LPAREN unionAlt ( COMMA unionAlt )* RPAREN
unionAlt ::= identifier dataType
fieldDef ::= identifier dataType OPTIONAL? ( DEFAULT jsonString )?
jsonString
         ::= STRING_LIT
typeName ::= identifier
createStream
         ::= CREATE ( LOG | COMPACT ) STREAM identifier streamTypeDef+
streamTypeDef
         ::= TYPE ( inlineStruct | qname ) AS typeAlias distributionClause?
distributionClause
         ::= DISTRIBUTE BY LPAREN identifier ( COMMA identifier )* RPAREN
inlineStruct
         ::= LPAREN fieldDef ( COMMA fieldDef )* RPAREN
typeAlias
         ::= identifier
dataType ::= primitiveType
           | compositeType
           | complexType
primitiveType
         ::= BOOL
           | INT8
           | INT16
           | INT32
           | INT64
           | FLOAT32
           | FLOAT64
           | STRING
           | ( ( CHAR | FIXED | TIME | TIMESTAMP | TIMESTAMP_TZ ) LPAREN | DECIMAL LPAREN INTEGER_LIT COMMA ) INTEGER_LIT RPAREN
           | BYTES
           | UUID
           | DATE
compositeType
         ::= ( LIST LPAREN | MAP LPAREN primitiveType COMMA ) dataType RPAREN
complexType
         ::= qname
expr     ::= orExpr
orExpr   ::= andExpr ( OR andExpr )*
andExpr  ::= notExpr ( AND notExpr )*
notExpr  ::= NOT* cmpExpr
cmpExpr  ::= addExpr ( ( EQ | NEQ | GT | LT | GTE | LTE | BETWEEN addExpr AND ) addExpr | IS NOT? NULL | IN LPAREN literal ( COMMA literal )* RPAREN )*
addExpr  ::= mulExpr ( ( PLUS | MINUS ) mulExpr )*
mulExpr  ::= unaryExpr ( ( STAR | SLASH | PERCENT ) unaryExpr )*
unaryExpr
         ::= MINUS* ( LPAREN expr RPAREN | literal | accessor )
literal  ::= NULL
           | TRUE
           | FALSE
           | INTEGER_LIT
           | NUMBER_LIT
           | STRING_LIT
           | BYTES_LIT
           | listLiteral
           | mapLiteral
listLiteral
         ::= LBRACK ( literal ( COMMA literal )* )? RBRACK
mapLiteral
         ::= LBRACE ( mapEntry ( COMMA mapEntry )* )? RBRACE
mapEntry ::= literal COLON literal
qname    ::= identifier ( DOT identifier )*
identifier
         ::= ID

<?TOKENS?>

EOF      ::= $
```

