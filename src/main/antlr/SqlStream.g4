grammar SqlStream;

@lexer::members {
  private int angleDepth = 0;
  private boolean pendingType = false;
  public void markTypeStart() { pendingType = true; }
  public void consumePendingType() { pendingType = false; }
}

/* ──────────────────── Entry Point ──────────────────── */
script
  : includeSection? (statement SEMI)+ EOF
  ;

includeSection
  : (includePragma SEMI)+
  ;

includePragma
  : INCLUDE STRING_LIT
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
  : USE CONTEXT (DOT)? qname
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
  : LIST LT dataType GT
  | MAP  LT primitiveType COMMA dataType GT
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

/* use a shift level that uses SHL/SHR lexed only when angleDepth==0 */
cmpExpr
  : mulExpr ((EQ | NEQ | GT | LT | GTE | LTE) mulExpr
           | IS NULL
           | IS NOT NULL
           | BETWEEN mulExpr AND mulExpr
           | IN LPAREN literal (COMMA literal)* RPAREN
           )*
  ;

// remove separate shiftExpr rule and fold SHL/SHR into mulExpr so shifts have same precedence as multiply
mulExpr
  : unaryExpr ((STAR | SLASH | PERCENT | SHL | SHR) unaryExpr)*
  ;

addExpr
  : mulExpr ((PLUS | MINUS) mulExpr)*
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


// Keywords (case-insensitive)
INCLUDE       : [Ii][Nn][Cc][Ll][Uu][Dd][Ee] ;
CREATE        : [Cc][Rr][Ee][Aa][Tt][Ee] ;
CONTEXT       : [Cc][Oo][Nn][Tt][Ee][Xx][Tt] ;
USE           : [Uu][Ss][Ee] ;
SCALAR        : [Ss][Cc][Aa][Ll][Aa][Rr] ;
ENUM          : [Ee][Nn][Uu][Mm] ;
STRUCT        : [Ss][Tt][Rr][Uu][Cc][Tt] ;
UNION         : [Uu][Nn][Ii][Oo][Nn] ;
STREAM        : [Ss][Tt][Rr][Ee][Aa][Mm] ;
READ          : [Rr][Ee][Aa][Dd] ;
WRITE         : [Ww][Rr][Ii][Tt][Ee] ;
FROM          : [Ff][Rr][Oo][Mm] ;
TO            : [Tt][Oo] ;
LOG           : [Ll][Oo][Gg] ;
COMPACT       : [Cc][Oo][Mm][Pp][Aa][Cc][Tt] ;
TYPE          : [Tt][Yy][Pp][Ee] ;
WHERE         : [Ww][Hh][Ee][Rr][Ee] ;
AS            : [Aa][Ss] ;
OPTIONAL      : [Oo][Pp][Tt][Ii][Oo][Nn][Aa][Ll] ;
VALUES        : [Vv][Aa][Ll][Uu][Ee][Ss] ;
OR            : [Oo][Rr] ;
AND           : [Aa][Nn][Dd] ;
XOR           : [Xx][Oo][Rr] ;
IS            : [Ii][Ss] ;
NOT           : [Nn][Oo][Tt] ;
DEFAULT       : [Dd][Ee][Ff][Aa][Uu][Ll][Tt] ;
TRUE          : [Tt][Rr][Uu][Ee] ;
FALSE         : [Ff][Aa][Ll][Ss][Ee] ;
NULL          : [Nn][Uu][Ll][Ll] ;
BOOL          : [Bb][Oo][Oo][Ll] ;
INT8          : [Ii][Nn][Tt] '8' ;
INT16         : [Ii][Nn][Tt] '16' ;
INT32         : [Ii][Nn][Tt] '32' ;
INT64         : [Ii][Nn][Tt] '64' ;
FLOAT32       : [Ff][Ll][Oo][Tt] '32' ;
FLOAT64       : [Ff][Ll][Oo][Tt] '64' ;
DECIMAL       : [Dd][Ee][Cc][Ii][Mm][Aa][Ll] ;
STRING        : [Ss][Tt][Rr][Ii][Nn][Gg] ;
CHAR          : [Cc][Hh][Aa][Rr] ;
BYTES         : [Bb][Yy][Tt][Ee][Ss] ;
FIXED         : [Ff][Ii][Xx][Ee][Dd] ;
UUID          : [Uu][Uu][Ii][Dd] ;
DATE          : [Dd][Aa][Tt][Ee] ;
TIME          : [Tt][Ii][Mm][Ee] ;
TIMESTAMP     : [Tt][Ii][Mm][Ee][Ss][Tt][Aa][Mm][Pp] ;
TIMESTAMP_TZ  : [Tt][Ii][Mm][Ee][Ss][Tt][Aa][Mm][Pp] '_' [Tt][Zz] ;
LIST          : [Ll][Ii][Ss][Tt] { markTypeStart(); } ;
MAP           : [Mm][Aa][Pp] { markTypeStart(); } ;
DISTRIBUTE    : [Dd][Ii][Ss][Tt][Rr][Ii][Bb][Uu][Tt][Ee] ;
BY            : [Bb][Yy] ;
BETWEEN       : [Bb][Ee][Tt][Ww][Ee][Ee][Nn] ;
IN            : [Ii][Nn] ;
OF            : [Oo][Ff] ;
MASK          : [Mm][Aa][Ss][Kk] ;
CHECK         : [Cc][Hh][Ee][Cc][Kk] ;

// Punctuation / operators
STAR          : '*' ;
COMMA         : ',' ;
DOT           : '.' ;
COLON         : ':' ;
LPAREN        : '(' ;
RPAREN        : ')' ;
LBRACK        : '[' ;
RBRACK        : ']' ;
LBRACE        : '{' ;
RBRACE        : '}' ;

// multi-char shift tokens — only when not inside a type (angleDepth==0) AND not pending a type
SHL           : {angleDepth==0 && !pendingType}? '<<' ;
SHR           : {angleDepth==0 && !pendingType}? '>>' ;

// relational multi-char tokens (must come before single-char LT/GT)
LTE           : '<=' ;
GTE           : '>=' ;
NEQ           : '<>' ;
EQ            : '=' ;

// single-char angle tokens update the type-nesting counter
LT            : '<' { if (pendingType) { angleDepth++; consumePendingType(); } } ;
GT            : '>' { if (angleDepth > 0) angleDepth--; } ;

SEMI          : ';' ;
PLUS          : '+' ;
MINUS         : '-' ;
SLASH         : '/' ;
PERCENT       : '%' ;
AMP           : '&' ;
PIPE          : '|' ;
CARET         : '^' ;
TILDE         : '~' ;

// Literals / identifiers
INTEGER_LIT     : '-'? [0-9]+ ;
NUMBER_LIT      : '-'? [0-9]+ '.' ([0-9]+ | [eE] [+-]? [0-9]+)? ;
STRING_LIT      : '\'' ( ~[\r\n'] | '\'\'' )* '\'' ;
BYTES_LIT       : [0][xX][0-9a-fA-F]+ ;
ID              : [a-zA-Z] [a-zA-Z0-9_]* ;

// Whitespace / comments
WS       : [ \t\r\n]+ -> skip ;
COMMENT  : '--' ~[\r\n]* -> skip ;
