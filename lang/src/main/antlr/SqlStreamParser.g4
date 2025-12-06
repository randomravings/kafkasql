parser grammar SqlStreamParser;

options {
  tokenVocab=SqlStreamLexer;
}

/* ──────────────────── Entry Point ──────────────────── */
script
  : includeSection? statementList EOF
  ;

/* ─────────────────────── Includes ────────────────────── */
includeSection
  : (includePragma SEMI)+
  ;

includePragma
  : INCLUDE STRING_LIT
  ;

/* ─────────────────────── Statements ─────────────────── */
statementList
  : statement (SEMI statement)* SEMI?
  ;

statement
  : useStmt
  | showStmt
  | explainStmt
  | readStmt
  | writeStmt
  | createStmt
  ;

/* ─────────────────────── Use Statement ─────────────────── */
useStmt
  : USE contextUse
  ;

contextUse
  : CONTEXT (qname | GLOBAL)
  ;

/* ─────────────────────── Show Statements ─────────────────── */
showStmt
  : SHOW CURRENT CONTEXT                       # ShowCurrentStmt
  | SHOW ALL showTarget                        # ShowAllStmt
  | SHOW showTarget qname?                     # ShowContextualStmt
  ;

showTarget
  : CONTEXTS
  | TYPES
  | STREAMS
  ;

/* ─────────────────────── Explain Statement ─────────────────── */
explainStmt
  : EXPLAIN qname
  ;

/* ─────────────────────── Read Statements ─────────────────── */
readStmt
  : READ FROM qname readBlockList
  ;

readBlockList
  : readBlock+
  ;

readBlock
  : TYPE typeName readProjection whereClause?
  ;

readProjection
  : STAR
  | readProjectionList
  ;

readProjectionList
  : readProjectionExpr (COMMA readProjectionExpr)*
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
  : WRITE TO qname TYPE typeName
    VALUES LPAREN writeValueList RPAREN
  ;

writeValueList
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
  : CONTEXT contextName declTailFragments
  ;

contextName
  : identifier
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

booleanType      : BOOLEAN ;
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
  : TYPE typeName
    AS typeKindDecl
    declTailFragments
  ;

typeName
  : identifier
  ;

typeKindDecl
  : scalarDecl
  | enumDecl
  | structDecl
  | unionDecl
  | derivedType
  ;

/* ─────────────────────── Scalar Decl ─────────────────── */
scalarDecl
  : SCALAR
    type
  ;

/* ─────────────────────── Enum Decl ─────────────────── */
enumDecl
  : ENUM enumType
    enumSymbolList
  ;

enumType
  : type?
  ;

enumSymbolList
  : LPAREN enumSymbol (COMMA enumSymbol)* RPAREN
  ;

enumSymbol
  : identifier EQ constExpr declTailFragments
  ;

/* ─────────────────────── Struct Decl ─────────────────── */
structDecl
  : STRUCT fieldList
  ;

fieldList
  : LPAREN fieldDecl (COMMA fieldDecl)* RPAREN
  ;

fieldDecl
  : identifier
    type
    nullableMarker?
    declTailFragments
  ;

nullableMarker
  : NULL
  ;

/* ─────────────────────── Union Decl ─────────────────── */
unionDecl
  : UNION unionMemberList
  ;

unionMemberList
  : LPAREN unionMemberDecl (COMMA unionMemberDecl)* RPAREN
  ;

unionMemberDecl
  : identifier
    type
    declTailFragments
  ;

/* ─────────────────────── Type Reference ─────────────────── */
typeReference
  : qname
  ;

/* ─────────────────────── Derived Type ─────────────────── */

derivedType
  : typeReference
  ;

/* ─────────────────────── Stream Decl ─────────────────── */
streamDecl
  : STREAM streamName
    LPAREN streamTypeDeclList RPAREN
    declTailFragments
  ;

streamName
  : identifier
  ;

streamTypeDeclList
  : streamTypeDecl (COMMA streamTypeDecl)*
  ;

streamTypeDecl
  : typeDecl
    declTailFragments
  ;

/* ─────────────────────── Stream Helpers ─────────────────── */
distributeFragment
  : DISTRIBUTE BY LPAREN identifier (COMMA identifier)* RPAREN
  ;

timestampFragment
  : TIMESTAMP BY LPAREN identifier RPAREN
  ;

/* ─────────────────────── Checks ─────────────────── */

declTailFragments
  : declTailFragment*
  ;

declTailFragment
  : constraintFragment
  | namedConstraintFragment
  | commentFragment
  | distributeFragment
  | timestampFragment
  ;

defaultFragment
  : DEFAULT literal
  ;

checkFragment
  : CHECK LPAREN expr RPAREN
  ;

namedConstraintFragment
  : CONSTRAINT identifier LPAREN constraintFragment RPAREN
  ;

constraintFragment
  : checkFragment
  | defaultFragment
  ;
  
commentFragment
  : COMMENT STRING_LIT
  ;

/* ─────────────0 Const Expressions ───────────── */
constExpr
  : constTerm ((PLUS | MINUS | STAR | SLASH | PERCENT
              | SHL | SHR | AMP | PIPE | XOR) constTerm)*
  ;

constTerm
  : NUMBER_LIT
  | constSymbolRef
  | LPAREN constExpr RPAREN
  ;

constSymbolRef
  : identifier
  ;

/* ──────────────── Expressions ──────────────── */
expr
  : orExpr
  ;

orExpr      : andExpr (OR andExpr)* ;
andExpr     : notExpr (AND notExpr)* ;
notExpr     : NOT notExpr | cmpExpr ;

cmpExpr
  : concatExpr ((EQ | NEQ | GT | LT | GTE | LTE) concatExpr
              | IS NULL
              | IS NOT NULL
              | BETWEEN concatExpr AND concatExpr
              | IN LPAREN literal (COMMA literal)* RPAREN
              )*
  ;

concatExpr  : shiftExpr (PIPE_PIPE shiftExpr)* ;

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
  : AT LBRACE (structEntry (COMMA structEntry)*)? RBRACE
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
