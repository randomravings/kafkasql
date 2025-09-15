parser grammar SqlStreamParser;

options {
  tokenVocab=SqlStreamLexer;
}

/* ──────────────────── Entry Point ──────────────────── */
script
  : includeSection? (statement SEMI)+ EOF
  ;

/* ─────────────────────── Includes ────────────────────── */
includeSection
  : (includePragma SEMI)+
  ;

includePragma
  : INCLUDE STRING_LIT
  ;

/* ─────────────────────── Statements ─────────────────── */
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

/* ─────────────────────── Use Statement ─────────────────── */
useStmt
  : useContext
  ;

useContext
  : USE CONTEXT (DOT)? qname
  ;

/* ─────────────────────── Read Statements ─────────────────── */
readStmt
  : READ FROM streamName typeBlock+
  ;

streamName
  : qname
  ;

typeBlock
  : TYPE typeName readProjection whereClause?
  ;

readProjection
  : STAR
  | (readProjectionExpr (COMMA readProjectionExpr)*)?
  ;

readProjectionExpr
  : expr (AS identifier)?
  ;

whereClause
  : WHERE expr
  ;

/* ─────────────────────── Write Statements ─────────────────── */
writeStmt
  : WRITE TO streamName TYPE typeName
    VALUES LPAREN writeValues RPAREN
  ;

writeValues
  :  structLiteral (COMMA structLiteral)*
  ;

/* ─────────────────────── Create Statements ─────────────────── */
createContext
  : CREATE CONTEXT identifier
  ;

createScalar
  : CREATE SCALAR typeName AS primitiveType
    (CHECK LPAREN expr RPAREN)?
    (DEFAULT literalValue)?
  ;

createEnum
  : CREATE (ENUM | MASK) typeName
    (AS enumType)?
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
  : identifier COLON NUMBER_LIT
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
  : identifier dataType (OPTIONAL)? (DEFAULT literal)?
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
  | CHAR          LPAREN NUMBER_LIT RPAREN
  | BYTES
  | FIXED         LPAREN NUMBER_LIT RPAREN
  | UUID
  | DATE
  | TIME          LPAREN NUMBER_LIT RPAREN
  | TIMESTAMP     LPAREN NUMBER_LIT RPAREN
  | TIMESTAMP_TZ  LPAREN NUMBER_LIT RPAREN
  | DECIMAL       LPAREN NUMBER_LIT COMMA NUMBER_LIT RPAREN
  ;

compositeType
  : LIST LT dataType GT
  | MAP  LT primitiveType COMMA dataType GT
  ;

complexType
  : qname
  ;

/* ──────────────── Expressions ──────────────── */
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

/* Comparison level uses shiftExpr operands so add/sub bind tighter than shifts */
cmpExpr
  : shiftExpr ((EQ | NEQ | GT | LT | GTE | LTE) shiftExpr
           | IS NULL
           | IS NOT NULL
           | BETWEEN shiftExpr AND shiftExpr
           | IN LPAREN literal (COMMA literal)* RPAREN
           )*
  ;

/* Shift operators now come after add/sub in precedence (add/sub bind tighter) */
shiftExpr
  : addExpr ((SHL | SHR) addExpr)*
  ;

addExpr
  : mulExpr ((PLUS | MINUS) mulExpr)*
  ;

mulExpr
  : unaryExpr ((STAR | SLASH | PERCENT) unaryExpr)*
  ;

/* Postfix/member/index expressions */
unaryExpr
  : MINUS unaryExpr
  | postfixExpr
  ;

postfixExpr
  : primary (memberAccess | indexAccess)*
  ;

memberAccess
  : DOT identifier
  ;

indexAccess
  : LBRACK expr RBRACK
  ;

primary
  : LPAREN expr RPAREN
  | literal
  | identifier
  ;

/* ──────────────── Literals ──────────────── */
literal
  : NULL
  | literalValue
  | structLiteral
  | enumLiteral
  | unionLiteral
  | literalSeq
  ;

literalValue
  : TRUE
  | FALSE
  | NUMBER_LIT
  | STRING_LIT
  | BYTES_LIT
  ;

literalSeq
  : listLiteral
  | mapLiteral
  ;

listLiteral
  : LBRACK
    (literal (COMMA literal)*)?
    RBRACK
  ;

mapLiteral
  : LBRACE
    (mapEntry (COMMA mapEntry)*)?
    RBRACE
  ;

mapEntry
  : literalValue COLON literal
  ;

structLiteral
  : LBRACE
    (structEntry (COMMA structEntry)*)?
    RBRACE
  ;

structEntry
  : identifier COLON literal
  ;

unionLiteral
  : identifier DOLLAR literal
  ;

enumLiteral
  : identifier DOUBLE_COLON identifier
  ;

/* ──────────────── Identifiers ──────────────── */

qname
  : identifier (DOT identifier)*
  ;

identifier
  : ID
  ;
