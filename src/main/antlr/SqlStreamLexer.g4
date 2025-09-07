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
IS            : [Ii][Ss] ;
NOT           : [Nn][Oo][Tt] ;
DEFAULT       : [Dd][Ee][Ff][Aa][Uu][Ll][Tt] ;
TRUE          : [Tt][Rr][Uu][Ee] ;
FALSE         : [Ff][Aa][Ll][Ss][Ee] ;
NULL          : [Nn][Uu][Ll][Ll] ;
BOOL          : [Bb][Oo][Oo][Ll] ;
INT8          : [Ii][Nn][Tt] '8' ;
UINT8         : [Uu][Ii][Nn][Tt] '8' ;
INT16         : [Ii][Nn][Tt] '16' ;
UINT16        : [Uu][Ii][Nn][Tt] '16' ;
INT32         : [Ii][Nn][Tt] '32' ;
UINT32        : [Uu][Ii][Nn][Tt] '32' ;
INT64         : [Ii][Nn][Tt] '64' ;
UINT64        : [Uu][Ii][Nn][Tt] '64' ;
FLOAT32       : [Ff][Ll][Oo][Tt] '32' ;
FLOAT64       : [Ff][Ll][Oo][Tt] '64' ;
DECIMAL       : [Dd][Ee][Cc][Ii][Mm][Aa][Ll] ;
STRING        : [Ss][Tt][Rr][Ii][Nn][Gg] ;
FSTRING       : [Ff][Ss][Tt][Rr][Ii][Nn][Gg] ;
BYTES         : [Bb][Yy][Tt][Ee][Ss] ;
FBYTES        : [Ff][Bb][Yy][Tt][Ee][Ss] ;
UUID          : [Uu][Uu][Ii][Dd] ;
DATE          : [Dd][Aa][Tt][Ee] ;
TIME          : [Tt][Ii][Mm][Ee] ;
TIMESTAMP     : [Tt][Ii][Mm][Ee][Ss][Tt][Aa][Mm][Pp] ;
TIMESTAMP_TZ  : [Tt][Ii][Mm][Ee][Ss][Tt][Aa][Mm][Pp] '_' [Tt][Zz] ;
LIST          : [Ll][Ii][Ss][Tt] ;
MAP           : [Mm][Aa][Pp] ;
DISTRIBUTE    : [Dd][Ii][Ss][Tt][Rr][Ii][Bb][Uu][Tt][Ee] ;
BY            : [Bb][Yy] ;

// Punctuation / operators
STAR          : '*' ;
COMMA         : ',' ;
DOT           : '.' ;
COLON         : ':' ;
LPAREN        : '(' ;
RPAREN        : ')' ;
LBRACK        : '[' ;
RBRACK        : ']' ;
LT            : '<' ;
GT            : '>' ;
EQ            : '=' ;
NEQ           : '!=' | '<>' ;
LTE           : '<=' ;
GTE           : '>=' ;
SEMI          : ';' ;



// Literals / identifiers
INT8_V          : '-'? [0-9]+ 'y';
UINT8_V         : [0-9]+ 'uy' ;
INT16_V         : '-'? [0-9]+ 's' ;
UINT16_V        : [0-9]+ 'us' ;
INT32_V         : '-'? [0-9]+ 'i'? ;
UINT32_V        : [0-9]+ 'u' ;
INT64_V         : '-'? [0-9]+ 'l' ;
UINT64_V        : [0-9]+ 'ul' ;
FLOAT32_V       : '-'? [0-9]+ ('.' [0-9]+)? ([eE] [+-]? [0-9]+)? 'f' ;
FLOAT64_V       : '-'? [0-9]+ ('.' [0-9]+)? ([eE] [+-]? [0-9]+)? 'd'? ;
DECIMAL_V       : '-'? [0-9]+ ('.' [0-9]+)? 'm' ;
STRING_V        : '\'' ( ~[\r\n'] | '\'\'' )* '\'' ;
FSTRING_V       : '\'' ( ~[\r\n'] | '\'\'' )* '\'!' ;
BYTES_V         : [0][xX][0-9a-fA-F]+ ;
FBYTES_V        : [0][xX][0-9a-fA-F]+ '!';
UUID_V          : 'id\'' HEX8 '-' HEX4 '-' HEX4 '-' HEX4 '-' HEX12 '\'' ;
DATE_V          : 'dt\'' DEC4 '-' DEC2 '-' DEC2 '\'' ;
TIME_V          : 'tm\'' YEAR ':' MONTH ':' DAY ('.' FRACTION)? '\'' ;
TIMESTAMP_V     : 'ts\'' YEAR '-' MONTH '-' DAY 'T' HOUR ':' MINUTE ':' SECOND ('.' FRACTION)? 'Z' '\'' ;
TIMESTAMP_TZ_V  : 'tz\'' YEAR '-' MONTH '-' DAY 'T' HOUR ':' MINUTE ':' SECOND ('.' FRACTION)? ([+-] HOUR ':' MINUTE) '\'' ;
ID              : [a-zA-Z] [a-zA-Z0-9_]* ;

fragment HEX12     : HEX8 HEX4 ;
fragment HEX8      : HEX4 HEX4 ;
fragment HEX4      : HEX HEX HEX HEX ;
fragment DEC4      : DEC2 DEC2 ;
fragment DEC2      : DEC DEC ;
fragment YEAR      : DEC4 ;
fragment MONTH     : DEC2 ;
fragment DAY       : DEC2 ;
fragment HOUR      : DEC2 ;
fragment MINUTE    : DEC2 ;
fragment SECOND    : DEC2 ;
fragment FRACTION  : DEC DEC? DEC? DEC? DEC? DEC? DEC? DEC? DEC? ;
fragment DEC       : [0-9] ;
fragment HEX       : [0-9a-fA-F] ;

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
