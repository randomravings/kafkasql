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
  : USE useContext
  ;

useType
  : useContext
  ;

useContext
  : CONTEXT qname
  ;

/* ─────────────────────── Read Statements ─────────────────── */
readStmt
  : READ FROM streamName readBlockList
  ;

readBlockList
  : readBlock+
  ;

readBlock
  : TYPE streamTypeName readProjection whereClause?
  ;

readProjection
  : STAR
  | (readProjectionExpr (COMMA readProjectionExpr)*)?
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
  : WRITE TO streamName TYPE streamTypeName
    VALUES LPAREN writeValues RPAREN
  ;

writeValues
  :  structLiteral (COMMA structLiteral)*
  ;

/* ─────────────────────── Create Statements ─────────────────────── */
createStmt
  : docComment? CREATE objDef
  ;
  
objDef
  : context
  | complexType
  | stream
  ;

context
  : CONTEXT qname
  ;

/* ─────────────────────── Type System ─────────────────── */
type
  : primitiveType
  | compositeType
  | complexType
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
  | charType
  | bytesType
  | fixedType
  | uuidType
  | dateType
  | timeType
  | timestampType
  | timestampTzType
  | decimalType
  ;

booleanType
  : BOOL
  ;

int8Type
  : INT8
  ;

int16Type
  : INT16
  ;

int32Type
  : INT32
  ;

int64Type
  : INT64
  ;

float32Type
  : FLOAT32
  ;

float64Type
  : FLOAT64
  ;

stringType
  : STRING
  ;

charType
  : CHAR LPAREN NUMBER_LIT RPAREN
  ;

bytesType
  : BYTES
  ;

fixedType
  : FIXED LPAREN NUMBER_LIT RPAREN
  ;

uuidType
  : UUID
  ;

dateType
  : DATE
  ;

timeType
  : TIME LPAREN NUMBER_LIT RPAREN
  ;

timestampType
  : TIMESTAMP LPAREN NUMBER_LIT RPAREN
  ;

timestampTzType
  : TIMESTAMP_TZ LPAREN NUMBER_LIT RPAREN
  ;

decimalType
  : DECIMAL LPAREN NUMBER_LIT COMMA NUMBER_LIT RPAREN
  ;

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

complexType
  : scalarType
  | enumType
  | structType
  | unionType
  ;

scalarType
  : SCALAR qname AS primitiveType
    scalarDefaultValue?
    scalarCheckExpr?
  ;

scalarDefaultValue
  : DEFAULT LPAREN literalValue RPAREN
  ;

scalarCheckExpr
  : checkExpr
  ;

enumType
  : ENUM enumName
    (AS enumBaseType)?
    enumSymbolList
    enumDefaultValue?
  ;

enumName 
  : qname
  ;

enumBaseType
  : INT8
  | INT16
  | INT32
  | INT64
  ;

enumSymbolList
  : LPAREN enumSymbol (COMMA enumSymbol)* RPAREN
  ;

enumSymbol
  : docComment? enumSymbolName COLON enumSymbolValue
  ;

enumSymbolName
  : identifier
  ;

enumSymbolValue
  : NUMBER_LIT
  ;

enumDefaultValue
  : DEFAULT LPAREN enumLiteral RPAREN
  ;

structType
  : STRUCT structName fieldList checkExpr?
  ;

structName
  : qname
  ;

fieldList
  : LPAREN field (COMMA field)* RPAREN
  ;

field
  : docComment? fieldName fieldType fieldNullable? fieldDefaultValue?
  ;

fieldName
  : identifier
  ;

fieldType
  : type
  ;

fieldNullable
  : NULL
  ;

fieldDefaultValue
  : DEFAULT LPAREN literal RPAREN
  ;

unionType
  : UNION unionName unionMemberList unionDefaultValue?
  ;

unionName
  : qname
  ;

unionMemberList
  : LPAREN unionMember (COMMA unionMember)* RPAREN
  ;

unionMember
  : unionMemberName unionMemberType
  ;

unionMemberName
  : identifier
  ;

unionMemberType
  : type
  ;

unionDefaultValue
  : DEFAULT LPAREN unionLiteral RPAREN
  ;

typeReference
  : qname
  ;

stream
  : STREAM streamName AS streamTypeList
  ;

streamName
  : qname
  ;

streamType
  : docComment? TYPE (streamTypeInline | streamTypeReference) AS streamTypeName distributeClause? timestampClause? checkExpr*
  ;

streamTypeList
  : streamType+
  ;

streamTypeName
  :  identifier
  ;

distributeClause
  : DISTRIBUTE BY LPAREN fieldName (COMMA fieldName)* RPAREN
  ;

timestampClause
  : WITH TIMESTAMP LPAREN fieldName RPAREN
  ;

streamTypeReference
  : typeReference
  ;

streamTypeInline
  : fieldList
  ;

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
  : unionName DOLLAR unionMemberName LPAREN literal RPAREN
  ;

enumLiteral
  : enumName DOUBLE_COLON enumSymbolName
  ;

/* ──────────────── Identifiers ──────────────── */

qname
  : dotPrefix? identifier (DOT identifier)*
  ;

dotPrefix
  : DOT
  ;

identifier
  : ID
  ;

docComment
  : DOC_COMMENT
  ;
