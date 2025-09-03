# KAFKA SQL

The idea behind this project is to create a structured query language for Kafka that can be used to do IO and resource management.
> DISCLAIMIER: This is currently just a side project to demonstrate an idea with code that might become intersting. This is very much subject to change, but if you find it intersting, I am very intersted to hear from you.

While there exists tools out there that enable you to interact with Kafka such as KSQL, Flink, and others - these are additions to, and run along side Kafka as opposed to be native to Kafka. What is more is that they are indended for Stream Processing which is in our case a secondary stage. What we are looking to do is to focus on more basic operations such as `CREATE` for adding Kafka resources and `READ`/`WRITE` for IO.

Since Kafka is branded as a streaming platform, we will consider topics to be streams, and to keep the nomenclature consistent we use read/write. We also want to refrain from use `INSERT`/`SELECT` as these are CRUD operations associated with RDBMS tables.

Initially there will be no `JOIN`, `GROUP BY`, `ORDER BY`, or any operations that hint at statful stream processing. We might consider stateless processibng like `WHERE`, `LIMIT`, `OFFSET` (set tarting point), and some simple field computations like `CAST`, `CONCAT`, `COALESCE`, or binary operators.

The current resources we focus on are:

- `CONTEX`: Functions like a namespace and will manifest physically as topic prefix.
- `STREAM`: This a topic and prefixed with the context it is created in. The statement will be accompanties with a schema and distribution/partition field list.
- `TYPE`: This is equialent to a schema for a reusable type that can reference each other or used by a topic.

And some future ones:

- `USER`: For `authn` operations
- `ACL`: For `authz` operations

Additionally are having `USE`statements for setting current context. In the near future we want to be adding `SHOW`/`EXPLAIN` so we can browse understand existing resources, and then and `ALTER`/`DROP` to complete the management.

## Intended use

The Kafka SQL language is indended to be use interactively, which SQL languages shine, but also be used in a CLI that additionally supports code generation to support DevOps pipelines, IDEs, or general offline activities. This is also why we will have an `INCLUCDE` pragma statemnt that is only used in CLI mode to allow us to split statements into multiple files to support Data as Code practices.

## Long term view

The inital idea is to create a client wrapper (Admin, Producer, and Consumer) to make it practical. The next challenge then is how to store the metadata.

The other long term idea is if it gains enough traction is to build it into the broker software so that server side validation, filtering, and even field level security can become possible.

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

Scalars are intended for strongly typed primitive types.

```SQL
CREATE SCALAR Email AS STRING;      -- Creates a strongly types email as string
```

Here are some thougts to add validation rules expressed as `CHECK` constraints to allow reusable, semantically validated types. We use STRING as opposed to CHAR or VARCHAR to keep it a bit more modern.

### Enums

```SQL
CREATE ENUM Ccy (
    EUR = 0,
    USD = 1,
    CNY = 2
);
```

Not sure if the values are needed, but since most programming languages have them, we will enable representation here. The other thing might be to allow for `DEFAULT` value on the type it self which is enabled in some serialization formats.

### Structs

This is where it gets more interesting, as there are a lot of considerations to take care of, but here are some examples:

```SQL
CREATE STRUCT Person (
    Name STRING,
    DataOfBirth DATE OPTIONAL
);
```

Here is a more complete example that shows includes in action.

```SQL
CREATE STRUCT User (
    Id INT32,
    Email com.example.Email,
    Score OPTIONAL DEFAULT 0.0
)
```

It is probably intuitive to C coders, or people used to working with protobuf or Avro IDL that we need to include file for every resource used. We only reference the immdediately referenced ones as the resolution is recursive and it will detect and fail if there are cyclic refereces (only files currently). The Avro style (list of types) would be an option, but we like naming the types for additional context.

### Unions

The choice here is whether to follow a oneof keyword like example in protobuf and JSON Schema, or whether to use a C style union. The choice was to create a C style union because it can then be a type to be reused and govered as such. Of course this can be debated.

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
- Many topics -> one Schema, which is about redicing redundancy when it is the case that multiple topics have the same structure.
- One topic -> many Schemmas, which is when different types have to be handled in order, also to reduce used partition count.
- Many topics -> many Schemas, imagine a combination of the above.

The first three can be covered when using [Confluent Schema Registry](https://docs.confluent.io/platform/current/clients/confluent-kafka-dotnet/_site/api/Confluent.SchemaRegistry.SubjectNameStrategy.html). But the way we do it, it is possible to add the fourth, and then we can debate whether it is a good idea.

Here is the basic inline stream definition (one topic one schena):

```SQL
CREATE STREAM Users
TYPE (                          -- The type prefix marks the beginning of a type.
    Id INT32,
    Email com.example.Email,
    Score OPTIONAL DEFAULT 0.0
) AS User                       -- All types must have an alias which will be show to be important
DISTRIBUTE BY (Id);             -- A comma separated list of fields that are concatenated and hashed in to the Kafka record key (not yet supported).
```

Here is a basic reference stream definition (many topics one schema)

```SQL
CREATE STREAM Users
TYPE com.example.User AS User
DISTRIBUTE BY (Id);
```

Every stream can have types added to them, but we can mix and match inline or reference (one topic many schemas or many topics many schemas):

```SQL
CREATE STREAM Users
TYPE (                          -- The type prefix marks the beginning of a type.
    Id INT32,
    Email com.example.Email,
    Score OPTIONAL DEFAULT 0.0
) AS User                       -- All types must have an alias which will be show to be important
DISTRIBUTE BY (Id);             -- A comma separated list of fields that are concatenated and hashed in to the Kafka record key (not yet supported).
```

Here is a basic reference stream definition (many topics one schema)

```SQL
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

Not very interesting as the types are the same, but it shows the idea. Distribute statements must be per type as they may vary.

## Lexer (EBNF)

```EBNF
/* converted on Wed Sep 3, 2025, 13:46 (UTC+02) by antlr_4-to-w3c v0.73-SNAPSHOT which is Copyright (c) 2011-2025 by Gunther Rademacher <grd@gmx.net> */

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
IS       ::= [Ii] [Ss]
NOT      ::= [Nn] [Oo] [Tt]
DEFAULT  ::= [Dd] [Ee] [Ff] [Aa] [Uu] [Ll] [Tt]
TRUE     ::= [Tt] [Rr] [Uu] [Ee]
FALSE    ::= [Ff] [Aa] [Ll] [Ss] [Ee]
NULL     ::= [Nn] [Uu] [Ll] [Ll]
BOOL     ::= [Bb] [Oo] [Oo] [Ll]
INT8     ::= [Ii] [Nn] [Tt] '8'
UINT8    ::= [Uu] [Ii] [Nn] [Tt] '8'
INT16    ::= [Ii] [Nn] [Tt] '16'
UINT16   ::= [Uu] [Ii] [Nn] [Tt] '16'
INT32    ::= [Ii] [Nn] [Tt] '32'
UINT32   ::= [Uu] [Ii] [Nn] [Tt] '32'
INT64    ::= [Ii] [Nn] [Tt] '64'
UINT64   ::= [Uu] [Ii] [Nn] [Tt] '64'
SINGLE   ::= [Ss] [Ii] [Nn] [Gg] [Ll] [Ee]
DOUBLE   ::= [Dd] [Oo] [Uu] [Bb] [Ll] [Ee]
DECIMAL  ::= [Dd] [Ee] [Cc] [Ii] [Mm] [Aa] [Ll]
STRING   ::= [Ss] [Tt] [Rr] [Ii] [Nn] [Gg]
FSTRING  ::= [Ff] [Ss] [Tt] [Rr] [Ii] [Nn] [Gg]
BYTES    ::= [Bb] [Yy] [Tt] [Ee] [Ss]
FBYTES   ::= [Ff] [Bb] [Yy] [Tt] [Ee] [Ss]
UUID     ::= [Uu] [Uu] [Ii] [Dd]
DATE     ::= [Dd] [Aa] [Tt] [Ee]
TIME     ::= [Tt] [Ii] [Mm] [Ee]
TIMESTAMP
         ::= [Tt] [Ii] [Mm] [Ee] [Ss] [Tt] [Aa] [Mm] [Pp]
TIMESTAMP_TZ
         ::= [Tt] [Ii] [Mm] [Ee] [Ss] [Tt] [Aa] [Mm] [Pp] '_' [Tt] [Zz]
LIST     ::= [Ll] [Ii] [Ss] [Tt]
MAP      ::= [Mm] [Aa] [Pp]
NEQ      ::= '!='
           | '<>'
STRING_LIT
         ::= "'" ( [^'#xd#xa] | "''" )* "'"
NUMBER   ::= '-'? [0-9]+ ( '.' [0-9]+ )?
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
/* converted on Wed Sep 3, 2025, 13:49 (UTC+02) by antlr_4-to-w3c v0.73-SNAPSHOT which is Copyright (c) 2011-2025 by Gunther Rademacher <grd@gmx.net> */

script   ::= includeSection? ( statement SEMI )+ EOF
includeSection
         ::= ( includePragma SEMI )+
includePragma
         ::= INCLUDE filePath
filePath ::= FILE_PATH
statement
         ::= useStmt
           | dmlStmt
           | ddlStmt
useStmt  ::= useContext
useContext
         ::= USE CONTEXT qname
dmlStmt  ::= readStmt
           | writeStmt
readStmt ::= READ FROM streamName typeBlock+
streamName
         ::= qname
typeBlock
         ::= TYPE typeName projection whereClause?
projection
         ::= projectionItem ( COMMA projectionItem )*
projectionItem
         ::= STAR
           | columnName
whereClause
         ::= WHERE booleanExpr
writeStmt
         ::= WRITE TO streamName TYPE typeName LPAREN fieldPathList RPAREN VALUES tuple ( COMMA tuple )*
fieldPathList
         ::= fieldPath ( COMMA fieldPath )*
fieldPath
         ::= identifier pathSeg*
pathSeg  ::= DOT identifier
           | LBRACK ( NUMBER | STRING_LIT ) RBRACK
tuple    ::= LPAREN literalOnlyList RPAREN
literalOnlyList
         ::= valueLit ( COMMA valueLit )*
valueLit ::= STRING_LIT
           | NUMBER
           | TRUE
           | FALSE
           | NULL
           | identifier
ddlStmt  ::= createStmt
createStmt
         ::= createContext
           | createScalar
           | createEnum
           | createStruct
           | createUnion
           | createStream
createContext
         ::= CREATE CONTEXT identifier
createScalar
         ::= CREATE SCALAR typeName AS primitiveType
createEnum
         ::= CREATE ENUM typeName LPAREN enumEntry ( COMMA enumEntry )* RPAREN
enumEntry
         ::= identifier EQ NUMBER
createStruct
         ::= CREATE STRUCT typeName LPAREN fieldDef ( COMMA fieldDef )* RPAREN
createUnion
         ::= CREATE UNION typeName LPAREN unionAlt ( COMMA unionAlt )* RPAREN
unionAlt ::= identifier COLON dataType
fieldDef ::= identifier dataType OPTIONAL? ( DEFAULT jsonString )?
jsonString
         ::= STRING_LIT
typeName ::= identifier
createStream
         ::= CREATE ( LOG | COMPACT ) STREAM identifier AS streamTypeDef+
streamTypeDef
         ::= TYPE ( inlineStruct | qname ) AS typeAlias
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
           | UINT8
           | INT16
           | UINT16
           | INT32
           | UINT32
           | INT64
           | UINT64
           | SINGLE
           | DOUBLE
           | STRING
           | ( ( FSTRING | FBYTES | TIME | TIMESTAMP | TIMESTAMP_TZ ) LPAREN | DECIMAL LPAREN NUMBER COMMA ) NUMBER RPAREN
           | BYTES
           | UUID
           | DATE
compositeType
         ::= ( LIST LT | MAP LT primitiveType COMMA ) dataType GT
complexType
         ::= qname
booleanExpr
         ::= orExpr
orExpr   ::= andExpr ( OR andExpr )*
andExpr  ::= notExpr ( AND notExpr )*
notExpr  ::= NOT* predicate
predicate
         ::= value ( cmpOp value | IS NOT? NULL )
           | LPAREN booleanExpr RPAREN
value    ::= literal
           | columnName
columnName
         ::= identifier ( DOT identifier )*
literal  ::= STRING_LIT
           | NUMBER
           | TRUE
           | FALSE
           | NULL
cmpOp    ::= EQ
           | NEQ
           | LT
           | LTE
           | GT
           | GTE
qname    ::= DOT? identifier ( DOT identifier )*
identifier
         ::= ID

<?TOKENS?>

EOF      ::= $
```

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
  -h, --help          Show this help
```

Add all possible disclaimers, this us early days ...