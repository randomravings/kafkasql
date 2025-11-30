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
  | createStmt
  ;

/* ─────────────────────── Use Statement ─────────────────── */
useStmt
  : USE contextUse
  ;

contextUse
  : CONTEXT qname
  ;

/* ─────────────────────── Read Statements ─────────────────── */
readStmt
  : READ FROM qname readBlockList
  ;

readBlockList
  : readBlock+
  ;

readBlock
  : TYPE streamTypeName readProjection whereClause?
  ;

readProjection
  : STAR
  | readProjectionExpr (COMMA readProjectionExpr)*
  ;

readProjectionExpr
  : expr (AS fieldAlias)?
  ;

whereClause
  : WHERE expr
  ;

fieldAlias
  : identifier
  ;

/* ─────────────────────── Write Statements ─────────────────── */
writeStmt
  : WRITE TO qname TYPE streamTypeName
    VALUES LPAREN writeValues RPAREN
  ;

writeValues
  : structLiteral (COMMA structLiteral)*
  ;

/* ─────────────────────── Create Statements ─────────────────────── */
createStmt
  : CREATE decl
  ;

decl
  : contextDecl
  | typeDecl
  | streamDecl
  ;

/* ─────────────────────── Context Decl ─────────────────── */
contextDecl
  : CONTEXT contextName contextTail*
  ;

contextName
  : identifier
  ;

contextTail
  : commentClause
  ;

/* ─────────────────────── Type System ─────────────────── */
type
  : primitiveType
  | compositeType
  | typeReference
  ;

primitiveType
  : booleanType
  | int8Type
  | int16Type
  | int32Type
  | int64Type
  | float32Type
  | float64Type
  | stringType
  | bytesType
  | uuidType
  | dateType
  | timeType
  | timestampType
  | timestampTzType
  | decimalType
  ;

booleanType      : BOOL ;
int8Type         : INT8 ;
int16Type        : INT16 ;
int32Type        : INT32 ;
int64Type        : INT64 ;
float32Type      : FLOAT32 ;
float64Type      : FLOAT64 ;
stringType       : STRING (LPAREN NUMBER_LIT RPAREN)? ;
bytesType        : BYTES (LPAREN NUMBER_LIT RPAREN)? ;
uuidType         : UUID ;
dateType         : DATE ;
timeType         : TIME (LPAREN NUMBER_LIT RPAREN)? ;
timestampType    : TIMESTAMP (LPAREN NUMBER_LIT RPAREN)? ;
timestampTzType  : TIMESTAMP_TZ (LPAREN NUMBER_LIT RPAREN)? ;
decimalType      : DECIMAL LPAREN NUMBER_LIT COMMA NUMBER_LIT RPAREN ;

compositeType
  : listType
  | mapType
  ;

listType
  : LIST LT type GT
  ;

mapType
  : MAP LT primitiveType COMMA type GT
  ;

/* ─────────────────────── Complex Type Decl ─────────────────── */
typeDecl
  : scalarDecl
  | enumDecl
  | structDecl
  | unionDecl
  ;

/* ─────────────────────── Scalar Decl ─────────────────── */
scalarDecl
  : SCALAR scalarName AS primitiveType
    scalarTail*
  ;

scalarName
  : identifier
  ;

scalarTail
  : scalarDefault
  | scalarCheck
  | commentClause
  ;

scalarDefault
  : DEFAULT LPAREN literalValue RPAREN
  ;

scalarCheck
  : CHECK LPAREN expr RPAREN
  ;

/* ─────────────────────── Enum Decl ─────────────────── */
enumDecl
  : ENUM enumName (AS enumBaseType)?
    enumSymbolList
    enumTail*
  ;

enumName
  : identifier
  ;

enumBaseType
  : INT8 | INT16 | INT32 | INT64
  ;

enumSymbolList
  : LPAREN enumSymbol (COMMA enumSymbol)* RPAREN
  ;

enumSymbol
  : identifier COLON NUMBER_LIT enumSymbolTail*
  ;

enumDefault
  : DEFAULT LPAREN enumLiteral RPAREN
  ;

enumTail
  : enumDefault
  | commentClause
  ;

enumSymbolTail
  : commentClause
  ;

/* ─────────────────────── Struct Decl ─────────────────── */
structDecl
  : STRUCT structName fieldList
    structTail*
  ;

structName
  : identifier
  ;

fieldList
  : LPAREN fieldDecl (COMMA fieldDecl)* RPAREN
  ;

structCheck
  : CHECK identifier LPAREN expr RPAREN
  ;

fieldDecl
  : identifier type fieldTail*
  ;

structTail
  : structCheck
  | commentClause
  ;

fieldTail
  : fieldNullable
  | fieldDefault
  | commentClause
;

fieldNullable
  : NULL
  ;

fieldDefault
  : DEFAULT literal
  ;

/* ─────────────────────── Union Decl ─────────────────── */
unionDecl
  : UNION unionName unionMemberList unionTail*
  ;

unionName
  : identifier
  ;

unionMemberList
  : LPAREN unionMemberDecl (COMMA unionMemberDecl)* RPAREN
  ;

unionMemberDecl
  : identifier type unionMemberTail*
  ;

unionTail
  : unionDefault
  | commentClause
  ;

unionMemberTail
  : commentClause
  ;

unionDefault
  : DEFAULT LPAREN unionLiteral RPAREN
  ;

/* ─────────────────────── Type Reference ─────────────────── */
typeReference
  : qname
  ;

/* ─────────────────────── Stream Decl ─────────────────── */
streamDecl
  : STREAM streamName AS streamTypeDeclList
    streamTail*
  ;

streamName
  : identifier
  ;

streamTypeDeclList
  : streamTypeDecl+
  ;

streamTypeDecl
  : TYPE (streamTypeInline | streamTypeRef)
    AS streamTypeName
    streamTypeTail*
  ;

streamTypeName
  : identifier
  ;

streamTypeRef
  : typeReference
  ;

streamTypeInline
  : fieldList
  ;

streamTail
  : commentClause
  ;

streamTypeTail
  : distributeClause
  | timestampClause
  | checkExpr
  | commentClause
  ;

/* ─────────────────────── Stream Helpers ─────────────────── */
distributeClause
  : DISTRIBUTE BY LPAREN identifier (COMMA identifier)* RPAREN
  ;

timestampClause
  : TIMESTAMP BY LPAREN identifier RPAREN
  ;

/* ─────────────────────── Checks ─────────────────── */
checkExpr
  : CHECK checkExprName? LPAREN expr RPAREN
  ;

checkExprName
  : identifier AS
  ;

/* ──────────────── Expressions ──────────────── */
expr
  : orExpr
  ;

orExpr      : andExpr (OR andExpr)* ;
andExpr     : notExpr (AND notExpr)* ;
notExpr     : NOT notExpr | cmpExpr ;

cmpExpr
  : shiftExpr ((EQ | NEQ | GT | LT | GTE | LTE) shiftExpr
              | IS NULL
              | IS NOT NULL
              | BETWEEN shiftExpr AND shiftExpr
              | IN LPAREN literal (COMMA literal)* RPAREN
              )*
  ;

shiftExpr   : addExpr ((SHL | SHR) addExpr)* ;
addExpr     : mulExpr ((PLUS | MINUS) mulExpr)* ;
mulExpr     : unaryExpr ((STAR | SLASH | PERCENT) unaryExpr)* ;

unaryExpr
  : MINUS unaryExpr
  | postfixExpr
  ;

postfixExpr
  : primary (memberAccess | indexAccess)*
  ;

memberAccess : DOT identifier ;
indexAccess  : LBRACK expr RBRACK ;

/* ──────────────── Primary Exprs ──────────────── */
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
  | listLiteral
  | mapLiteral
  ;

literalValue
  : TRUE
  | FALSE
  | NUMBER_LIT
  | STRING_LIT
  | BYTES_LIT
  ;

listLiteral
  : LBRACK (literal (COMMA literal)*)? RBRACK
  ;

mapLiteral
  : LBRACE (mapEntry (COMMA mapEntry)*)? RBRACE
  ;

mapEntry
  : literalValue COLON literal
  ;

structLiteral
  : LBRACE (structEntry (COMMA structEntry)*)? RBRACE
  ;

structEntry
  : identifier COLON literal
  ;

unionLiteral
  : qname DOLLAR identifier LPAREN literal RPAREN
  ;

enumLiteral
  : qname DOUBLE_COLON identifier
  ;

/* ──────────────── Identifiers / Docs ──────────────── */
qname
  : identifier (DOT identifier)*
  ;

dotPrefix
  : DOT
  ;

identifier
  : ID
  ;

commentClause
  : COMMENT STRING_LIT
  ;
