parser grammar SqlStreamParser;

options { tokenVocab=SqlStreamLexer; }

/* ──────────────────── Entry Point ──────────────────── */
script
  : includeSection? (statement SEMI)+ EOF
  ;

includeSection
  : (includePragma SEMI)+
  ;

includePragma
  : INCLUDE filePath
  ;

filePath
  : FILE_PATH
  ;

statement
  : useStmt
  | dmlStmt
  | ddlStmt
  ;

useStmt
  : useContext
  ;

useContext
  : USE CONTEXT qname
  ;

dmlStmt
  : readStmt
  | writeStmt
  ;

readStmt
  : READ FROM streamName typeBlock+
  ;

streamName
  : qname
  ;

typeBlock
  : TYPE typeName projection whereClause?
  ;

projection
  : STAR
  | fieldPath (COMMA fieldPath)*
  ;

whereClause
  : WHERE booleanExpr
  ;

writeStmt
  : WRITE TO streamName TYPE typeName
    LPAREN fieldPathList RPAREN
    VALUES tuple (COMMA tuple)*
  ;

fieldPathList
  : fieldPath (COMMA fieldPath)*
  ;

tuple
  : LPAREN literalOnlyList RPAREN
  ;

literalOnlyList
  : literal (COMMA literal)*
  ;

ddlStmt
  : createStmt
  ;

createStmt
  : createContext
  | createScalar
  | createEnum
  | createStruct
  | createUnion
  | createStream
  ;

createContext
  : CREATE CONTEXT identifier
  ;

createScalar
  : CREATE SCALAR typeName AS primitiveType
  ;

createEnum
  : CREATE ENUM typeName
    LPAREN enumSymbol (COMMA enumSymbol)* RPAREN
  ;

enumSymbol
  : identifier EQ INT32_V
  ;

createStruct
  : CREATE STRUCT typeName
    LPAREN fieldDef (COMMA fieldDef)* RPAREN
  ;

createUnion
  : CREATE UNION typeName
    LPAREN unionAlt (COMMA unionAlt)* RPAREN
  ;

unionAlt
  : identifier dataType
  ;

fieldDef
  : identifier dataType (OPTIONAL)? (DEFAULT jsonString)?
  ;

jsonString
  : STRING_V
  ;

typeName
  : identifier
  ;

createStream
  : CREATE (LOG | COMPACT) STREAM identifier streamTypeDef+
  ;

streamTypeDef
  : TYPE (inlineStruct | qname) AS typeAlias distributionClause?
  ;

distributionClause
  : DISTRIBUTE BY LPAREN identifier (COMMA identifier)* RPAREN
  ;

inlineStruct
  : LPAREN fieldDef (COMMA fieldDef)* RPAREN
  ;

typeAlias
  : identifier
  ;

/* ─────────────────────── Type System ─────────────────── */
dataType
  : primitiveType
  | compositeType
  | complexType
  ;

primitiveType
  : BOOL
  | INT8
  | UINT8
  | INT16
  | UINT16
  | INT32
  | UINT32
  | INT64
  | UINT64
  | FLOAT32
  | FLOAT64
  | STRING
  | FSTRING       LPAREN INT32_V RPAREN
  | BYTES
  | FBYTES        LPAREN INT32_V RPAREN
  | UUID
  | DATE
  | TIME          LPAREN INT32_V RPAREN
  | TIMESTAMP     LPAREN INT32_V RPAREN
  | TIMESTAMP_TZ  LPAREN INT32_V RPAREN
  | DECIMAL       LPAREN INT32_V COMMA INT32_V RPAREN
  ;

compositeType
  : LIST LT dataType GT
  | MAP  LT primitiveType COMMA dataType GT
  ;

complexType
  : qname
  ;

/* ──────────────── Expressions (WHERE) ──────────────── */
booleanExpr
  : orExpr
  ;

orExpr
  : andExpr (OR andExpr)*
  ;

andExpr
  : notExpr (AND notExpr)*
  ;

notExpr
  : NOT notExpr
  | predicate
  ;

predicate
  : value cmpOp value                 # cmpPredicate
  | value IS NULL                     # isNullPredicate
  | value IS NOT NULL                 # isNotNullPredicate
  | LPAREN booleanExpr RPAREN         # parenPredicate
  ;

value
  : literal
  | fieldPath
  ;

fieldPath
  : identifier fieldPathSeg*
  ;

fieldPathSeg
  : DOT identifier
  | LBRACK INT32_V RBRACK
  | LBRACK STRING_V RBRACK
  ;

literal
  : nullLiteral
  | primitiveLiteral
  ;

nullLiteral
  : NULL
  ;

primitiveLiteral
  : booleanLiteral
  | numberLiteral
  | characterLiteral
  | uuidLiteral
  | temporalLiteral
  ;

booleanLiteral
  : TRUE
  | FALSE
  ;

numberLiteral
  : INT8_V
  | UINT8_V
  | INT16_V
  | UINT16_V
  | INT32_V
  | UINT32_V
  | INT64_V
  | UINT64_V
  | FLOAT32_V
  | FLOAT64_V
  | DECIMAL_V
  ;

characterLiteral
  : STRING_V
  | FSTRING_V
  | BYTES_V
  | FBYTES_V
  ;

uuidLiteral
  : UUID_V
  ;

temporalLiteral
  : DATE_V
  | TIME_V
  | TIMESTAMP_V
  | TIMESTAMP_TZ_V
  ;

cmpOp
  : EQ
  | NEQ
  | LT
  | LTE
  | GT
  | GTE
  ;

qname
  : (DOT)? identifier (DOT identifier)*
  ;

identifier
  : ID
  ;