lexer grammar SqlStreamLexer;

// Keywords (case-insensitive)
INCLUDE       : [Ii][Nn][Cc][Ll][Uu][Dd][Ee] {pushMode(FILEPATH_MODE);} ;
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
LIST          : [Ll][Ii][Ss][Tt] ;
MAP           : [Mm][Aa][Pp] ;
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
LT            : '<' ;
GT            : '>' ;
EQ            : '=' ;
NEQ           : '<>' ;
LTE           : '<=' ;
GTE           : '>=' ;
SEMI          : ';' ;
PLUS          : '+' ;
MINUS         : '-' ;
SLASH         : '/' ;
PERCENT       : '%' ;
AMP           : '&' ;
PIPE          : '|' ;
CARET         : '^' ;
TILDE         : '~' ;
SHL           : '<<' ;
SHR           : '>>' ;



// Literals / identifiers
INTEGER_LIT     : '-'? [0-9]+ ;
NUMBER_LIT      : '-'? [0-9]+ '.' ([0-9]+ | [eE] [+-]? [0-9]+)? ;
STRING_LIT      : '\'' ( ~[\r\n'] | '\'\'' )* '\'' ;
BYTES_LIT       : [0][xX][0-9a-fA-F]+ ;
ID              : [a-zA-Z] [a-zA-Z0-9_]* ;

// Whitespace / comments (default mode)
WS       : [ \t\r\n]+ -> skip ;
COMMENT  : '--' ~[\r\n]* -> skip ;

// ------------- FILE PATH MODE -------------
mode FILEPATH_MODE;

// Skip whitespace inside path mode too
WS_PATH : [ \t\r\n]+ -> skip ;

// Whole quoted path (pop back to default after)
FILE_PATH
  : '\'' PATH_SEG ( '/' PATH_SEG )* '.' FILE_EXT '\''
    {popMode();}
  ;

fragment PATH_SEG : [a-zA-Z0-9_-]+ ;
fragment FILE_EXT : [Ss][Qq][Ll][Ss] ;

