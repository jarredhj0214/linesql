parser grammar OracleParser;

options { tokenVocab = OracleLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : transactionStatement                                           #transactionStmt
    | query                                                          #statementDefault
    | insertStatement                                                #insertStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | mergeStatement                                                 #mergeStmt
    | createIndexStatement                                           #createIndexStmt
    | alterIndexStatement                                            #alterIndexStmt
    | dropIndexStatement                                             #dropIndexStmt
    | createRoutineStatement                                         #createRoutineStmt
    | anonymousBlockStatement                                        #anonymousBlockStmt
    | createTriggerStatement                                         #createTriggerStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropRoutineStatement                                           #dropRoutineStmt
    | dropTriggerStatement                                           #dropTriggerStmt
    | oracleSchemaObjectControlStatement                             #oracleSchemaObjectControlStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | truncateTableStatement                                         #truncateTableStmt
    | lockTableStatement                                             #lockTableStmt
    | grantStatement                                                 #grantStmt
    | revokeStatement                                                #revokeStmt
    | alterSessionStatement                                          #alterSessionStmt
    | alterMaterializedViewStatement                                 #alterMaterializedViewStmt
    | alterTableStatement                                            #alterTableStmt
    | analyzeTableStatement                                          #analyzeTableStmt
    | analyzeIndexStatement                                          #analyzeIndexStmt
    | explainPlanStatement                                           #explainPlanStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | commentStatement                                               #commentStmt
    ;

// ============ Query ============

query
    : ctes? queryTerm queryOrganization
    ;

ctes
    : WITH namedQuery (COMMA namedQuery)*
    ;

namedQuery
    : name=identifier (LPAREN columnAliases=identifierList RPAREN)? AS LPAREN query RPAREN
    ;

queryTerm
    : queryPrimary                                                   #queryTermDefault
    | left=queryTerm setOperator right=queryTerm                     #setOperation
    ;

setOperator
    : UNION ALL
    | UNION DISTINCT?
    | EXCEPT (ALL | DISTINCT)?
    | INTERSECT (ALL | DISTINCT)?
    | MINUS_SET (ALL | DISTINCT)?
    ;

queryPrimary
    : querySpecification                                             #queryPrimaryDefault
    | LPAREN query RPAREN                                            #subqueryPrimary
    ;

querySpecification
    : selectClause selectIntoClause? fromClause? whereClause? startWithClause? connectByClause? groupByClause? havingClause? modelClause?
    ;

selectClause
    : SELECT setQuantifier? selectItemList
    ;

selectIntoClause
    : (BULK COLLECT)? INTO identifierList
    ;

setQuantifier
    : DISTINCT
    | ALL
    ;

selectItemList
    : selectItem (COMMA selectItem)*
    ;

selectItem
    : expression (AS? alias=identifier)?                             #selectExpression
    | qualifiedName DOT STAR                                         #selectQualifiedStar
    | STAR                                                           #selectStar
    ;

fromClause
    : FROM relationList
    ;

relationList
    : relation (COMMA relation)*
    ;

relation
    : relationPrimary pivotClause* matchRecognizeClause? joinRelation*
    ;

relationPrimary
    : (TABLE LPAREN qualifiedName LPAREN expressionList? RPAREN RPAREN
      | qualifiedName LPAREN jsonTableArgumentList RPAREN
      | qualifiedName LPAREN xmlTableArgumentList RPAREN) tableAlias #tableFunctionRelation
    | multipartIdentifier partitionExtensionClause? flashbackClause? sampleClause? tableAlias #tableName
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    ;

jsonTableArgumentList
    : expression COMMA string COLUMNS LPAREN jsonTableColumn (COMMA jsonTableColumn)* RPAREN
    ;

jsonTableColumn
    : identifier dataType PATH string
    ;

xmlTableArgumentList
    : string PASSING expression COLUMNS LPAREN jsonTableColumn (COMMA jsonTableColumn)* RPAREN
    ;

partitionExtensionClause
    : (PARTITION | SUBPARTITION) LPAREN identifier RPAREN
    ;

flashbackClause
    : AS OF (SCN expression | TIMESTAMP expression)
    ;

sampleClause
    : SAMPLE BLOCK? LPAREN expression RPAREN (SEED LPAREN expression RPAREN)?
    ;

joinRelation
    : joinType? JOIN relationPrimary pivotClause* matchRecognizeClause? joinCriteria?
    | applyType relationPrimary pivotClause* matchRecognizeClause?
    ;

applyType
    : CROSS APPLY
    | OUTER APPLY
    ;

pivotClause
    : PIVOT XML? LPAREN pivotAggregation (COMMA pivotAggregation)* FOR pivotForExpression IN LPAREN pivotInItem (COMMA pivotInItem)* RPAREN RPAREN
    | UNPIVOT ((INCLUDE | EXCLUDE) NULLS)? LPAREN unpivotValueColumns FOR identifier IN LPAREN unpivotInItem (COMMA unpivotInItem)* RPAREN RPAREN
    ;

pivotAggregation
    : functionName LPAREN setQuantifier? expression RPAREN (AS? identifier)?
    ;

pivotForExpression
    : identifier
    | LPAREN identifierList RPAREN
    ;

pivotInItem
    : expression (AS? identifier)?
    | LPAREN expressionList RPAREN (AS? identifier)?
    ;

unpivotValueColumns
    : identifier
    | LPAREN identifierList RPAREN
    ;

unpivotInItem
    : identifier (AS? (identifier | string))?
    | LPAREN identifierList RPAREN (AS? (identifier | string))?
    ;

matchRecognizeClause
    : MATCH_RECOGNIZE LPAREN matchRecognizeOption* RPAREN tableAlias
    ;

matchRecognizeOption
    : PARTITION BY expressionList
    | ORDER BY sortItem (COMMA sortItem)*
    | MEASURES matchMeasure (COMMA matchMeasure)*
    | ALL ROWS PER MATCH
    | ONE ROW PER MATCH
    | AFTER MATCH SKIP_KEYWORD matchSkipOption
    | PATTERN LPAREN matchPatternItem+ RPAREN
    | DEFINE matchDefinition (COMMA matchDefinition)*
    ;

matchMeasure
    : expression AS? identifier
    ;

matchDefinition
    : identifier AS expression
    ;

matchSkipOption
    : PAST LAST ROW
    | TO NEXT ROW
    | TO identifier
    ;

matchPatternItem
    : identifier
    | LPAREN
    | RPAREN
    | LBRACE
    | RBRACE
    | PLUS
    | STAR
    | QUESTION
    | OR
    | COMMA
    | number
    ;

modelClause
    : MODEL modelReturnOption? modelOption+
    ;

modelReturnOption
    : RETURN (UPDATED | ALL) ROWS
    ;

modelOption
    : PARTITION BY LPAREN expressionList RPAREN
    | DIMENSION BY LPAREN expressionList RPAREN
    | MEASURES LPAREN modelMeasure (COMMA modelMeasure)* RPAREN
    | (IGNORE | KEEP) NAV
    | RULES (UPSERT ALL?)? LPAREN modelRuleContent* RPAREN
    ;

modelMeasure
    : expression (AS? identifier)?
    ;

modelRuleContent
    : LPAREN modelRuleContent* RPAREN
    | LBRACKET modelRuleContent* RBRACKET
    | identifier
    | number
    | string
    | COMMA
    | DOT
    | EQ
    | comparisonOperator
    | PLUS
    | MINUS
    | STAR
    | SLASH
    | PERCENT
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

joinCriteria
    : ON expression
    | USING LPAREN identifierList RPAREN
    ;

tableAlias
    : (AS? strictIdentifier)?
    ;

whereClause
    : WHERE expression
    ;

startWithClause
    : START WITH expression
    ;

connectByClause
    : CONNECT BY NOCYCLE? PRIOR? expression
    ;

groupByClause
    : GROUP BY groupByItem (COMMA groupByItem)*
    ;

groupByItem
    : expression
    | ROLLUP LPAREN expressionList RPAREN
    | CUBE LPAREN expressionList RPAREN
    | GROUPING SETS LPAREN groupingSet (COMMA groupingSet)* RPAREN
    ;

groupingSet
    : LPAREN expressionList? RPAREN
    | expression
    ;

havingClause
    : HAVING expression
    ;

queryOrganization
    : (ORDER SIBLINGS? BY sortItem (COMMA sortItem)*)?
      (LIMIT expression)?
      offsetClause?
      fetchClause?
      lockingClause?
    ;

lockingClause
    : FOR UPDATE (OF multipartIdentifier (COMMA multipartIdentifier)*)? (NOWAIT | WAIT expression | SKIP_KEYWORD LOCKED)?
    ;

offsetClause
    : OFFSET expression ROWS?
    ;

fetchClause
    : FETCH (FIRST | NEXT) expression PERCENT_KEYWORD? ROWS? (ONLY | WITH TIES)
    ;

sortItem
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

// ============ Expressions ============

expression
    : booleanExpression
    ;

booleanExpression
    : NOT booleanExpression                                          #logicalNot
    | valueExpression predicate?                                     #predicatedExpr
    | left=booleanExpression AND right=booleanExpression              #logicalAnd
    | left=booleanExpression OR right=booleanExpression               #logicalOr
    | EXISTS LPAREN query RPAREN                                     #existsExpr
    ;

predicate
    : NOT? BETWEEN lower=valueExpression AND upper=valueExpression
    | NOT? IN LPAREN (expressionList | query) RPAREN
    | NOT? LIKE valueExpression
    | IS NOT? NULL
    ;

valueExpression
    : primaryExpression                                              #valueExpressionDefault
    | operator=(MINUS | PLUS) valueExpression                        #unaryExpression
    | left=valueExpression operator=(STAR | SLASH | PERCENT) right=valueExpression   #arithmeticBinary
    | left=valueExpression operator=(PLUS | MINUS) right=valueExpression             #arithmeticBinaryPlusMinus
    | left=valueExpression CONCAT right=valueExpression              #concatExpression
    | left=valueExpression comparisonOperator right=valueExpression  #comparison
    ;

comparisonOperator
    : EQ | NEQ | LT | GT | LTE | GTE
    ;

primaryExpression
    : CASE whenClause+ (ELSE elseExpr=expression)? END              #searchedCase
    | CASE operand=expression whenClause+ (ELSE elseExpr=expression)? END  #simpleCase
    | CAST LPAREN expression AS dataType RPAREN                      #castExpr
    | functionName LPAREN STAR RPAREN keepClause? withinGroupClause? (OVER windowSpec)? #functionCallStar
    | functionName LPAREN setQuantifier? expressionList listaggOverflowClause? RPAREN keepClause? withinGroupClause? (OVER windowSpec)?  #functionCall
    | functionName LPAREN RPAREN keepClause? withinGroupClause? (OVER windowSpec)?      #functionCallEmpty
    | DATE string                                                    #dateLiteral
    | TIMESTAMP string                                               #timestampLiteral
    | CONNECT_BY_ROOT primaryExpression                              #connectByRootExpression
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | primaryExpression DOT identifier outerJoinMarker?               #dereference
    | identifier outerJoinMarker?                                     #columnReference
    | number                                                         #numberLiteral
    | string                                                         #stringLiteral
    | NULL                                                           #nullLiteral
    | TRUE                                                           #booleanTrue
    | FALSE                                                          #booleanFalse
    | INTERVAL expression identifier                                 #intervalLiteral
    | DEFAULT                                                        #defaultLiteral
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

outerJoinMarker
    : LPAREN PLUS RPAREN
    ;

windowSpec
    : LPAREN (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
    ;

keepClause
    : KEEP LPAREN identifier (FIRST | LAST) ORDER BY sortItem (COMMA sortItem)* RPAREN
    ;

withinGroupClause
    : WITHIN GROUP LPAREN ORDER BY sortItem (COMMA sortItem)* RPAREN
    ;

listaggOverflowClause
    : ON OVERFLOW (ERROR | TRUNCATE string? ((WITH | WITHOUT) identifier)?)
    ;

windowFrame
    : (ROWS | RANGE) windowFrameExtent
    ;

windowFrameExtent
    : windowFrameBound
    | BETWEEN lower=windowFrameBound AND upper=windowFrameBound
    ;

windowFrameBound
    : UNBOUNDED PRECEDING
    | UNBOUNDED FOLLOWING
    | CURRENT ROW
    | expression PRECEDING
    | expression FOLLOWING
    ;

functionName
    : identifier
    | LEFT
    | RIGHT
    | REPLACE
    | CAST
    | VALUES
    | DEFAULT
    ;

expressionList
    : expression (COMMA expression)*
    ;

// ============ DML Statements ============

insertStatement
    : INSERT INTO? TABLE? multipartIdentifier
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)*)
      returningClause?
    | INSERT (ALL | FIRST) multiTableInsertClause+ query
    ;

multiTableInsertClause
    : (WHEN condition=expression THEN)? INTO multipartIdentifier
      (LPAREN targetColumnList RPAREN)?
      VALUES LPAREN expressionList RPAREN
    ;

targetColumnList
    : identifier (COMMA identifier)*
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

updateStatement
    : UPDATE multipartIdentifier tableAlias SET assignmentList whereClause? returningClause?
    ;

deleteStatement
    : DELETE FROM multipartIdentifier tableAlias whereClause? returningClause?
    ;

returningClause
    : RETURNING expressionList INTO identifierList
    ;

mergeStatement
    : MERGE INTO multipartIdentifier tableAlias
      USING (multipartIdentifier tableAlias | LPAREN query RPAREN tableAlias)
      ON expression
      mergeClause+
    ;

mergeClause
    : WHEN MATCHED THEN mergeMatchedAction
    | WHEN NOT MATCHED THEN mergeNotMatchedAction
    ;

mergeMatchedAction
    : UPDATE SET assignmentList updateWhere=whereClause? (DELETE deleteWhere=whereClause)?
    ;

mergeNotMatchedAction
    : INSERT (LPAREN identifierList RPAREN)? VALUES LPAREN expressionList RPAREN whereClause?
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : multipartIdentifier EQ expression
    ;

// ============ DDL Statements ============

createIndexStatement
    : CREATE (UNIQUE | BITMAP)? INDEX multipartIdentifier
      ON multipartIdentifier LPAREN indexElementList RPAREN
      oracleIndexOption*
    ;

oracleIndexOption
    : LOCAL
    | GLOBAL? oraclePartitionClause
    | TABLESPACE multipartIdentifier
    | (LOGGING | NOLOGGING)
    | (PARALLEL | NOPARALLEL) number?
    | (COMPRESS | NOCOMPRESS) number?
    | ONLINE
    ;

alterIndexStatement
    : ALTER INDEX multipartIdentifier .+?
    ;

dropIndexStatement
    : DROP INDEX multipartIdentifier
    ;

createRoutineStatement
    : CREATE (OR REPLACE)? (PROCEDURE | FUNCTION) multipartIdentifier .+?
    | CREATE (OR REPLACE)? PACKAGE BODY? multipartIdentifier .+?
    ;

anonymousBlockStatement
    : DECLARE .+? BEGIN .+? END identifier?
    | BEGIN .+? END identifier?
    ;

createTriggerStatement
    : CREATE (OR REPLACE)? TRIGGER multipartIdentifier .+? ON multipartIdentifier .+?
    ;

indexElementList
    : indexElement (COMMA indexElement)*
    ;

indexElement
    : expression (ASC | DESC)?
    ;

createTableStatement
    : CREATE temporaryTableScope? TEMPORARY? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      oracleTableProperty*
      oracleTemporaryTableOption?
      (AS query)?
    | CREATE temporaryTableScope? TEMPORARY? TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

temporaryTableScope
    : GLOBAL
    | PRIVATE
    ;

oracleTableProperty
    : (LOGGING | NOLOGGING)
    | (PARALLEL | NOPARALLEL) number?
    | (COMPRESS | NOCOMPRESS)
    | ORGANIZATION EXTERNAL LPAREN oracleTableOptionContent* RPAREN
    | oraclePartitionClause
    ;

oracleTableOptionContent
    : LPAREN oracleTableOptionContent* RPAREN
    | identifier
    | number
    | string
    | COMMA
    | EQ
    | BY
    ;

oraclePartitionClause
    : PARTITION BY (RANGE | HASH | LIST) LPAREN identifierList RPAREN
      (PARTITIONS number | LPAREN oraclePartitionDefinition (COMMA oraclePartitionDefinition)* RPAREN)?
    ;

oraclePartitionDefinition
    : PARTITION identifier (VALUES (LESS THAN)? LPAREN expressionList RPAREN)?
    ;

oracleTemporaryTableOption
    : ON COMMIT ((DELETE | PRESERVE) ROWS | (DROP | PRESERVE) DEFINITION)
    ;

createViewStatement
    : CREATE (OR REPLACE)? (NO? FORCE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      viewBequeathOption?
      AS query
      viewCheckOption?
    | CREATE MATERIALIZED VIEW multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      materializedViewOption*
      AS query
    ;

materializedViewOption
    : BUILD (IMMEDIATE | DEFERRED)
    | REFRESH (FAST | COMPLETE | FORCE)? (ON (DEMAND | COMMIT))?
    ;

viewCheckOption
    : WITH (READ ONLY | CHECK OPTION) (CONSTRAINT identifier)?
    ;

viewBequeathOption
    : BEQUEATH (DEFINER | CURRENT_USER)
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifier dropTableOption*
    ;

dropTableOption
    : CASCADE CONSTRAINTS
    | PURGE
    ;

dropViewStatement
    : DROP MATERIALIZED? VIEW multipartIdentifier dropMaterializedViewOption*
    ;

dropMaterializedViewOption
    : PRESERVE TABLE
    | PURGE
    ;

dropRoutineStatement
    : DROP (PROCEDURE | FUNCTION) multipartIdentifier
    ;

dropTriggerStatement
    : DROP TRIGGER (IF EXISTS)? multipartIdentifier
    ;

oracleSchemaObjectControlStatement
    : CREATE SEQUENCE multipartIdentifier .+?
    | ALTER SEQUENCE multipartIdentifier .+?
    | DROP SEQUENCE multipartIdentifier
    | CREATE (OR REPLACE)? PUBLIC? SYNONYM multipartIdentifier FOR multipartIdentifier
    | DROP PUBLIC? SYNONYM multipartIdentifier
    | CREATE PUBLIC? DATABASE LINK multipartIdentifier .+?
    | DROP PUBLIC? DATABASE LINK multipartIdentifier
    ;

truncateTableStatement
    : TRUNCATE TABLE? multipartIdentifier truncateTableOption*
    ;

truncateTableOption
    : (DROP | REUSE) STORAGE
    ;

lockTableStatement
    : LOCK TABLE multipartIdentifier (COMMA multipartIdentifier)* IN lockMode MODE (NOWAIT | WAIT expression)?
    ;

lockMode
    : ROW SHARE
    | ROW EXCLUSIVE
    | SHARE UPDATE
    | SHARE
    | SHARE ROW EXCLUSIVE
    | EXCLUSIVE
    ;

grantStatement
    : GRANT privilegeList ON multipartIdentifier TO identifierList (WITH GRANT OPTION)?
    ;

revokeStatement
    : REVOKE privilegeList ON multipartIdentifier FROM identifierList
    ;

transactionStatement
    : COMMIT
    | ROLLBACK
    | SAVEPOINT identifier
    | SET TRANSACTION (READ (ONLY | WRITE) | ISOLATION LEVEL identifier)
    ;

privilegeList
    : privilege (COMMA privilege)*
    ;

privilege
    : SELECT
    | INSERT
    | UPDATE
    | DELETE
    | identifier
    ;

alterSessionStatement
    : ALTER SESSION SET identifier EQ? expression
    ;

alterMaterializedViewStatement
    : ALTER MATERIALIZED VIEW multipartIdentifier .+?
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME TO multipartIdentifier   #alterTableRename
    | ALTER TABLE multipartIdentifier ADD COLUMN? identifier dataType #alterTableAddColumn
    | ALTER TABLE multipartIdentifier alterTableAction                #alterTableOther
    ;

alterTableAction
    : DROP COLUMN identifier
    | SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

showStatement
    : SHOW .+? TABLE multipartIdentifier
    | SHOW .+?
    ;

describeStatement
    : (DESCRIBE | DESC) TABLE? multipartIdentifier identifier?
    ;

commentStatement
    : COMMENT ON (TABLE multipartIdentifier | COLUMN multipartIdentifier)
      IS string
    ;

analyzeTableStatement
    : ANALYZE TABLE multipartIdentifier analyzeTableAction
    ;

analyzeIndexStatement
    : ANALYZE INDEX multipartIdentifier analyzeIndexAction
    ;

explainPlanStatement
    : EXPLAIN PLAN explainPlanOption* FOR statement
    ;

explainPlanOption
    : SET identifier EQ string
    | INTO multipartIdentifier
    ;

analyzeTableAction
    : COMPUTE STATISTICS analyzeStatisticsOption*
    | ESTIMATE STATISTICS analyzeStatisticsOption*
    | DELETE STATISTICS
    | VALIDATE (identifier | STRUCTURE)?
    ;

analyzeIndexAction
    : COMPUTE STATISTICS analyzeStatisticsOption*
    | ESTIMATE STATISTICS analyzeStatisticsOption*
    | DELETE STATISTICS
    | VALIDATE STRUCTURE?
    ;

analyzeStatisticsOption
    : FOR TABLE
    | FOR ALL COLUMNS
    | FOR COLUMNS identifierList
    | SAMPLE NUMBER_LITERAL PERCENT_KEYWORD?
    ;

// ============ DDL Helpers ============

tableElementList
    : tableElement (COMMA tableElement)*
    ;

tableElement
    : identifier dataType columnConstraint*
    ;

columnConstraint
    : NOT NULL
    | NULL
    | COMMENT string
    | DEFAULT expression
    | PRIMARY KEY
    | UNIQUE
    ;

commentClause
    : COMMENT string
    ;

propertyList
    : property (COMMA property)*
    ;

property
    : string EQ string
    ;

dataType
    : identifier (LPAREN NUMBER_LITERAL (COMMA NUMBER_LITERAL)* RPAREN)?
    | identifier LT dataType (COMMA dataType)* GT
    ;

// ============ Common ============

multipartIdentifier
    : identifier (DOT identifier)* dbLinkSuffix?
    ;

dbLinkSuffix
    : AT identifier
    ;

qualifiedName
    : identifier (DOT identifier)*
    ;

identifierList
    : identifier (COMMA identifier)*
    ;

identifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | nonReservedKeyword
    ;

strictIdentifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | nonReservedKeyword
    ;

nonReservedKeyword
    : ADD | ANALYZE | APPLY | ASC | BITMAP | CASCADE | CAST | COLUMN | COLUMNS | COMMENT | COMPUTE | CONNECT_BY_ROOT | CONSTRAINT | CONSTRAINTS | DEFAULT
    | BEGIN | BEQUEATH | BODY | BUILD | CURRENT_USER | DATABASE | DECLARE | DEFERRED | DEFINER | DEMAND | DESCRIBE | DESC | DUAL | END | ERROR | EXISTS | EXPLAIN | EXTERNAL | FALSE
    | BULK | CHECK | COLLECT | COMPLETE | FAST | FETCH | FIRST | FOR | FORCE | FUNCTION | GRANT | IF | IMMEDIATE | INDEX | INTERVAL | LAST | LATERAL | LIKE | LIMIT | MATERIALIZED | MINUS_SET | NEXT | NO | NULL
    | PACKAGE | PASSING | PATH | PERCENT_KEYWORD | REFRESH
    | KEY | NOCYCLE | NULLS | OF | OFFSET | ONLY | OPTION | OVER | PARTITION | PLAN | PRIMARY | PRIVATE | PRIOR | PROCEDURE | PUBLIC | PURGE | READ | WRITE | ISOLATION | LEVEL | SERIALIZABLE | RENAME | REPLACE | REUSE | ROW | ROWS
    | CURRENT | UNBOUNDED | PRECEDING | FOLLOWING
    | RETURN | REVOKE | ROLLBACK | ROLLUP | SAVEPOINT | SEQUENCE | SESSION | TRANSACTION | SET | SETS | SHOW | SIBLINGS | START | STATISTICS | STORAGE | STRUCTURE | SYNONYM | TABLE | TEMPORARY | TO | TRIGGER | TRUE
    | TRUNCATE | VALUES | VIEW | DATE | ESTIMATE | LINK | NOWAIT | OVERFLOW | RETURNING | SCN | TIES | TIMESTAMP | UNIQUE | VALIDATE | WAIT | WITHIN | WITHOUT
    | SKIP_KEYWORD | LOCK | LOCKED | MODE | SHARE | EXCLUSIVE
    | PIVOT | UNPIVOT | MATCH_RECOGNIZE | MODEL | MEASURES | DIMENSION | RULES | UPSERT | UPDATED | IGNORE | KEEP | NAV
    | PATTERN | DEFINE | AFTER | MATCH | ONE | PER | PAST
    | INCLUDE | EXCLUDE | XML | SAMPLE | BLOCK | SEED | TABLESPACE | ONLINE | SUBPARTITION
    | RANGE | HASH | LIST | LOCAL | LESS | THAN | MAXVALUE | PARTITIONS | GROUPING | CUBE
    | LOGGING | NOLOGGING | PARALLEL | NOPARALLEL | COMPRESS | NOCOMPRESS | ORGANIZATION | GLOBAL | COMMIT | PRESERVE | DEFINITION
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
