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
INCLUDE 'com/example/Email.sqls'

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

## Current Parser (EBNF)

```EBNF
/* converted on Wed Sep 17, 2025, 16:36 (UTC+02) by antlr_3-to-w3c v0.73-SNAPSHOT which is Copyright (c) 2011-2025 by Gunther Rademacher <grd@gmx.net> */

script   ::= includeSection? ( statement SEMI )+ EOF
includeSection
         ::= ( includePragma SEMI )+
includePragma
         ::= INCLUDE STRING_LIT
statement
         ::= useStmt
           | readStmt
           | writeStmt
           | createStmt
useStmt  ::= useContext
useContext
         ::= USE CONTEXT qname
readStmt ::= READ FROM streamName readTypeBlock+
streamName
         ::= qname
readTypeBlock
         ::= TYPE identifier readProjection whereClause?
readProjection
         ::= ( STAR | readProjectionExpr ( COMMA readProjectionExpr )* )?
readProjectionExpr
         ::= expr ( AS identifier )?
whereClause
         ::= WHERE expr
writeStmt
         ::= WRITE TO streamName TYPE identifier VALUES LPAREN writeValues RPAREN
writeValues
         ::= structLiteral ( COMMA structLiteral )*
createStmt
         ::= CREATE objDef
objDef   ::= context
           | complexType
           | stream
context  ::= CONTEXT qname
type     ::= primitiveType
           | compositeType
           | complexType
           | typeReference
primitiveType
         ::= booleanType
           | int8Type
           | int16Type
           | int32Type
           | int64Type
           | float32Type
           | float64Type
           | stringType
           | charType
           | bytesType
           | fixedType
           | uuidType
           | dateType
           | timeType
           | timestampType
           | timestampTzType
           | decimalType
booleanType
         ::= BOOL
int8Type ::= INT8
int16Type
         ::= INT16
int32Type
         ::= INT32
int64Type
         ::= INT64
float32Type
         ::= FLOAT32
float64Type
         ::= FLOAT64
stringType
         ::= STRING
charType ::= CHAR LPAREN NUMBER_LIT RPAREN
bytesType
         ::= BYTES
fixedType
         ::= FIXED LPAREN NUMBER_LIT RPAREN
uuidType ::= UUID
dateType ::= DATE
timeType ::= TIME LPAREN NUMBER_LIT RPAREN
timestampType
         ::= TIMESTAMP LPAREN NUMBER_LIT RPAREN
timestampTzType
         ::= TIMESTAMP_TZ LPAREN NUMBER_LIT RPAREN
decimalType
         ::= DECIMAL LPAREN NUMBER_LIT COMMA NUMBER_LIT RPAREN
compositeType
         ::= listType
           | mapType
listType ::= LIST LT type GT
mapType  ::= MAP LT primitiveType COMMA type GT
complexType
         ::= scalarType
           | enumType
           | structType
           | unionType
scalarType
         ::= SCALAR qname AS primitiveType ( CHECK LPAREN expr RPAREN )? ( DEFAULT literalValue )?
enumType ::= ENUM qname ( AS enumBaseType )? LPAREN enumSymbol ( COMMA enumSymbol )* RPAREN ( DEFAULT enumSymbolName )?
enumBaseType
         ::= INT8
           | INT16
           | INT32
           | INT64
enumSymbol
         ::= enumSymbolName COLON enumSymbolValue
enumSymbolName
         ::= identifier
enumSymbolValue
         ::= NUMBER_LIT
structType
         ::= STRUCT qname fieldList
fieldList
         ::= LPAREN field ( COMMA field )* RPAREN
field    ::= fieldName fieldType fieldNullable? fieldDefaultValue?
fieldName
         ::= identifier
fieldType
         ::= type
fieldNullable
         ::= NULL
fieldDefaultValue
         ::= DEFAULT literal
unionType
         ::= UNION qname LPAREN unionMember ( COMMA unionMember )* RPAREN
unionMember
         ::= unionMemberName unionMemberType
unionMemberName
         ::= identifier
unionMemberType
         ::= type
typeReference
         ::= qname
stream   ::= STREAM qname streamTypeList
streamType
         ::= TYPE ( streamTypeInline | streamTypeReference ) AS streamTypeName distributeClause?
streamTypeList
         ::= streamType+
streamTypeName
         ::= identifier
distributeClause
         ::= DISTRIBUTE BY LPAREN fieldName ( COMMA fieldName )* RPAREN
streamTypeReference
         ::= typeReference
streamTypeInline
         ::= fieldList
expr     ::= orExpr
orExpr   ::= andExpr ( OR andExpr )*
andExpr  ::= notExpr ( AND notExpr )*
notExpr  ::= NOT* cmpExpr
cmpExpr  ::= shiftExpr ( ( EQ | NEQ | GT | LT | GTE | LTE | BETWEEN shiftExpr AND ) shiftExpr | IS NOT? NULL | IN LPAREN literal ( COMMA literal )* RPAREN )*
shiftExpr
         ::= addExpr ( ( SHL | SHR ) addExpr )*
addExpr  ::= mulExpr ( ( PLUS | MINUS ) mulExpr )*
mulExpr  ::= unaryExpr ( ( STAR | SLASH | PERCENT ) unaryExpr )*
unaryExpr
         ::= MINUS* postfixExpr
postfixExpr
         ::= primary ( memberAccess | indexAccess )*
memberAccess
         ::= DOT identifier
indexAccess
         ::= LBRACK expr RBRACK
primary  ::= LPAREN expr RPAREN
           | literal
           | identifier
literal  ::= NULL
           | literalValue
           | structLiteral
           | enumLiteral
           | unionLiteral
           | literalSeq
literalValue
         ::= TRUE
           | FALSE
           | NUMBER_LIT
           | STRING_LIT
           | BYTES_LIT
literalSeq
         ::= listLiteral
           | mapLiteral
listLiteral
         ::= LBRACK ( literal ( COMMA literal )* )? RBRACK
mapLiteral
         ::= LBRACE ( mapEntry ( COMMA mapEntry )* )? RBRACE
mapEntry ::= literalValue COLON literal
structLiteral
         ::= LBRACE ( structEntry ( COMMA structEntry )* )? RBRACE
structEntry
         ::= identifier COLON literal
unionLiteral
         ::= identifier DOLLAR literal
enumLiteral
         ::= identifier DOUBLE_COLON identifier
qname    ::= dotPrefix? identifier ( DOT identifier )*
dotPrefix
         ::= DOT
identifier
         ::= ID

<?TOKENS?>

EOF      ::= $
```

## Current Lexer (EBNF)

```EBNF
/* converted on Wed Sep 17, 2025, 16:35 (UTC+02) by antlr_4-to-w3c v0.73-SNAPSHOT which is Copyright (c) 2011-2025 by Gunther Rademacher <grd@gmx.net> */

_        ::= WS
           | COMMENT
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
TYPE     ::= [Tt] [Yy] [Pp] [Ee]
WHERE    ::= [Ww] [Hh] [Ee] [Rr] [Ee]
AS       ::= [Aa] [Ss]
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
CHECK    ::= [Cc] [Hh] [Ee] [Cc] [Kk]
NUMBER_LIT
         ::= '-'? [0-9]+ ( ( '.' | [eE] [+#x2D]? ) [0-9]+ )?
STRING_LIT
         ::= "'" ( [^'#xd#xa] | "''" )* "'"
BYTES_LIT
         ::= '0' [xX] [0-9a-fA-F]+
ID       ::= [a-zA-Z] [a-zA-Z0-9_]*
WS       ::= [ #x9#xd#xa]+
COMMENT  ::= '--' [^#xd#xa]*
```
