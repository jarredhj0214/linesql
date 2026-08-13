grammar SparkLineSql;

statement
    : query EOF
    | insertStatement EOF
    | ctasStatement EOF
    | createViewStatement EOF
    ;

insertStatement
    : INSERT (INTO | OVERWRITE) TABLE? target=tableName query
    ;

ctasStatement
    : CREATE TABLE (IF NOT EXISTS)? target=tableName AS query
    ;

createViewStatement
    : CREATE (OR REPLACE)? (TEMPORARY | TEMP)? VIEW (IF NOT EXISTS)? target=tableName AS query
    ;

query
    : cteClause? queryTerm (UNION ALL? queryTerm)*
    ;

cteClause
    : WITH namedQuery (COMMA namedQuery)*
    ;

namedQuery
    : identifier AS LPAREN query RPAREN
    ;

queryTerm
    : SELECT selectList fromClause? joinClause*
    ;

selectList
    : selectItem (COMMA selectItem)*
    ;

selectItem
    : expression ((AS)? identifier)?
    ;

fromClause
    : FROM relation
    ;

joinClause
    : joinType? JOIN relation (ON expression)?
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

relation
    : tableName alias?
    | LPAREN query RPAREN alias?
    ;

alias
    : AS? identifier
    ;

expression
    : primary ((STAR | DIVIDE | PLUS | MINUS | EQ | LT | GT) primary)*
    ;

primary
    : functionCall
    | tableName
    | literal
    | STAR
    | LPAREN expression RPAREN
    ;

functionCall
    : identifier LPAREN (expression (COMMA expression)*)? RPAREN
    ;

literal
    : STRING
    | NUMBER
    ;

tableName
    : identifier (DOT identifier)*
    ;

identifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    ;

SELECT: S E L E C T;
FROM: F R O M;
JOIN: J O I N;
ON: O N;
INSERT: I N S E R T;
INTO: I N T O;
OVERWRITE: O V E R W R I T E;
TABLE: T A B L E;
CREATE: C R E A T E;
VIEW: V I E W;
AS: A S;
WITH: W I T H;
UNION: U N I O N;
ALL: A L L;
OR: O R;
REPLACE: R E P L A C E;
TEMPORARY: T E M P O R A R Y;
TEMP: T E M P;
IF: I F;
NOT: N O T;
EXISTS: E X I S T S;
INNER: I N N E R;
LEFT: L E F T;
RIGHT: R I G H T;
FULL: F U L L;
OUTER: O U T E R;
CROSS: C R O S S;

COMMA: ',';
DOT: '.';
LPAREN: '(';
RPAREN: ')';
STAR: '*';
DIVIDE: '/';
PLUS: '+';
MINUS: '-';
EQ: '=';
LT: '<';
GT: '>';

BACKQUOTED_IDENTIFIER: '`' ( ~'`' | '``' )* '`';
IDENTIFIER: [a-zA-Z_\u0080-\uFFFF] [a-zA-Z_0-9$\u0080-\uFFFF]*;
NUMBER: [0-9]+ ('.' [0-9]+)?;
STRING: '\'' ( ~['\\] | '\\' . )* '\'';

LINE_COMMENT: '--' ~[\r\n]* -> channel(HIDDEN);
BLOCK_COMMENT: '/*' .*? '*/' -> channel(HIDDEN);
WS: [ \t\r\n]+ -> channel(HIDDEN);

fragment A: [aA];
fragment B: [bB];
fragment C: [cC];
fragment D: [dD];
fragment E: [eE];
fragment F: [fF];
fragment G: [gG];
fragment H: [hH];
fragment I: [iI];
fragment J: [jJ];
fragment K: [kK];
fragment L: [lL];
fragment M: [mM];
fragment N: [nN];
fragment O: [oO];
fragment P: [pP];
fragment Q: [qQ];
fragment R: [rR];
fragment S: [sS];
fragment T: [tT];
fragment U: [uU];
fragment V: [vV];
fragment W: [wW];
fragment X: [xX];
fragment Y: [yY];
fragment Z: [zZ];
