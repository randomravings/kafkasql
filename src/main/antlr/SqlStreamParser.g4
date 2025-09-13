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
  | readStmt
  | writeStmt
  | createContext
  | createScalar
  | createEnum
  | createStruct
  | createUnion
  | createStream
  ;

useStmt
  : useContext
  ;

useContext
  : USE CONTEXT qname
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

whereClause
  : WHERE expr
  ;

writeStmt
  : WRITE TO streamName TYPE typeName
    LPAREN projection RPAREN
    VALUES tuple (COMMA tuple)*
  ;

projection
  : STAR
  | accessor (COMMA accessor)*
  ;
  
accessor
  : identifier (DOT accessor)?
  | LBRACK literal RBRACK (DOT accessor)?
  ;

tuple
  : LPAREN literal (COMMA literal)* RPAREN
  ;

createContext
  : CREATE CONTEXT identifier
  ;

createScalar
  : CREATE SCALAR typeName AS primitiveType
    (CHECK LPAREN expr RPAREN)?
    (DEFAULT literal)?
  ;

createEnum
  : CREATE ENUM typeName
    (OF enumType)?
    (AS MASK)?
    LPAREN enumSymbol (COMMA enumSymbol)* RPAREN
    (DEFAULT identifier)?
  ;

enumType
  : INT8
  | INT16
  | INT32
  | INT64
  ;

enumSymbol
  : identifier COLON INTEGER_LIT
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
  : STRING_LIT
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
  | INT16
  | INT32
  | INT64
  | FLOAT32
  | FLOAT64
  | STRING
  | CHAR          LPAREN INTEGER_LIT RPAREN
  | BYTES
  | FIXED         LPAREN INTEGER_LIT RPAREN
  | UUID
  | DATE
  | TIME          LPAREN INTEGER_LIT RPAREN
  | TIMESTAMP     LPAREN INTEGER_LIT RPAREN
  | TIMESTAMP_TZ  LPAREN INTEGER_LIT RPAREN
  | DECIMAL       LPAREN INTEGER_LIT COMMA INTEGER_LIT RPAREN
  ;

compositeType
  : LIST LPAREN dataType RPAREN
  | MAP  LPAREN primitiveType COMMA dataType RPAREN
  ;

complexType
  : qname
  ;

/* ──────────────── Expressions (WHERE) ──────────────── */

expr
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
  | cmpExpr
  ;

cmpExpr
  : addExpr ((EQ | NEQ | GT | LT | GTE | LTE) addExpr
           | IS NULL
           | IS NOT NULL
           | BETWEEN addExpr AND addExpr
           | IN LPAREN literal (COMMA literal)* RPAREN
           )*
  ;

addExpr
  : mulExpr ((PLUS | MINUS) mulExpr)*
  ;

mulExpr
  : unaryExpr ((STAR | SLASH | PERCENT) unaryExpr)*
  ;

unaryExpr
  : MINUS unaryExpr
  | LPAREN expr RPAREN
  | literal
  | accessor
  ;

literal
  : NULL
  | TRUE
  | FALSE
  | INTEGER_LIT
  | NUMBER_LIT
  | STRING_LIT
  | BYTES_LIT
  | listLiteral
  | mapLiteral
  ;

listLiteral
  : LBRACK (literal (COMMA literal)*)? RBRACK
  ;

mapLiteral
  : LBRACE (mapEntry (COMMA mapEntry)*)? RBRACE
  ;

mapEntry
  : literal COLON literal
  ;

qname
  : identifier (DOT identifier)*
  ;

identifier
  : ID
  ;