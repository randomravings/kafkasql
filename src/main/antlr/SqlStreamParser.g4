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
  : projectionItem (COMMA projectionItem)*
  ;

projectionItem
  : STAR       # projectAll
  | columnName # projectCol
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

fieldPath
  : identifier pathSeg*
  ;

pathSeg
  : DOT identifier
  | LBRACK NUMBER RBRACK
  | LBRACK STRING_LIT RBRACK
  ;

tuple
  : LPAREN literalOnlyList RPAREN
  ;

literalOnlyList
  : valueLit (COMMA valueLit)*
  ;

valueLit
  : STRING_LIT
  | NUMBER
  | TRUE
  | FALSE
  | NULL
  | identifier
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
    LPAREN enumEntry (COMMA enumEntry)* RPAREN
  ;

enumEntry
  : identifier EQ NUMBER
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
  : identifier COLON dataType
  ;

fieldDef
  : identifier dataType (OPTIONAL)? (DEFAULT jsonString)?
  ;

jsonString
  : STRING_LIT
  ;

typeName
  : identifier
  ;

createStream
  : CREATE (LOG | COMPACT) STREAM identifier AS streamTypeDef+
  ;

streamTypeDef
  : TYPE (inlineStruct | qname) AS typeAlias
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
  | SINGLE
  | DOUBLE
  | STRING
  | FSTRING       LPAREN NUMBER RPAREN
  | BYTES
  | FBYTES        LPAREN NUMBER RPAREN
  | UUID
  | DATE
  | TIME          LPAREN NUMBER RPAREN
  | TIMESTAMP     LPAREN NUMBER RPAREN
  | TIMESTAMP_TZ  LPAREN NUMBER RPAREN
  | DECIMAL       LPAREN NUMBER COMMA NUMBER RPAREN
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
  | columnName
  ;

columnName
  : identifier (DOT identifier)*
  ;

literal
  : STRING_LIT
  | NUMBER
  | TRUE
  | FALSE
  | NULL
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