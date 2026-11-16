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
    | alterRoutineStatement                                          #alterRoutineStmt
    | anonymousBlockStatement                                        #anonymousBlockStmt
    | createTriggerStatement                                         #createTriggerStmt
    | principalStatement                                             #principalStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropRoutineStatement                                           #dropRoutineStmt
    | dropTriggerStatement                                           #dropTriggerStmt
    | oracleSchemaObjectControlStatement                             #oracleSchemaObjectControlStmt
    | flashbackTableStatement                                        #flashbackTableStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | truncateTableStatement                                         #truncateTableStmt
    | lockTableStatement                                             #lockTableStmt
    | grantStatement                                                 #grantStmt
    | revokeStatement                                                #revokeStmt
    | alterSessionStatement                                          #alterSessionStmt
    | alterSystemStatement                                           #alterSystemStmt
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
      searchClause? cycleClause?
    ;

searchClause
    : SEARCH (DEPTH | BREADTH) FIRST BY identifierList SET orderColumn=identifier
    ;

cycleClause
    : CYCLE identifierList SET markColumn=identifier TO string DEFAULT string
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
    : selectClause selectIntoClause? fromClause? whereClause? startWithClause? connectByClause? groupByClause? havingClause?
      (modelClause | windowClause qualifyClause? | qualifyClause)?
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
    : relationPrimary pivotClause* matchRecognizeClause? joinPartitionClause? joinRelation*
    ;

relationPrimary
    : (TABLE LPAREN qualifiedName LPAREN expressionList? RPAREN RPAREN
      | qualifiedName LPAREN jsonTableArgumentList RPAREN
      | XMLTABLE LPAREN xmlTableArgumentList RPAREN) tableAlias #tableFunctionRelation
    | multipartIdentifier partitionExtensionClause? flashbackClause? sampleClause? tableAlias #tableName
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    ;

jsonTableArgumentList
    : expression (FORMAT JSON)? (COMMA string)?
      jsonTableOnError? jsonTableOnEmpty? jsonTableColumns
    ;

jsonTableColumns
    : COLUMNS LPAREN jsonTableColumn (COMMA jsonTableColumn)* RPAREN
    ;

jsonTableColumn
    : name=identifier FOR ORDINALITY
    | name=identifier dataType? (EXISTS | FORMAT JSON jsonTableWrapper?)? (PATH string)?
      jsonTableOnError? jsonTableOnEmpty?
    | NESTED PATH? string jsonTableColumns
    ;

jsonTableWrapper
    : WITH (CONDITIONAL | UNCONDITIONAL)? ARRAY? WRAPPER
    | WITHOUT ARRAY? WRAPPER
    ;

jsonTableOnError
    : jsonTableBehavior ON ERROR
    ;

jsonTableOnEmpty
    : jsonTableBehavior ON EMPTY
    ;

jsonTableBehavior
    : ERROR | NULL | TRUE | FALSE | EMPTY (ARRAY | OBJECT)
    | DEFAULT (string | (PLUS | MINUS)? number)
    ;

xmlTableArgumentList
    : (XMLNAMESPACES LPAREN xmlNamespace (COMMA xmlNamespace)* RPAREN COMMA)?
      string (PASSING (BY VALUE)? xmlPassingArgument (COMMA xmlPassingArgument)*)?
      (RETURNING SEQUENCE BY REF)?
      (COLUMNS (LPAREN xmlTableColumn (COMMA xmlTableColumn)* RPAREN
              | xmlTableColumn (COMMA xmlTableColumn)*))?
    ;

xmlNamespace
    : DEFAULT string
    | string AS identifier
    ;

xmlPassingArgument
    : expression (AS identifier)?
    ;

xmlTableColumn
    : name=identifier FOR ORDINALITY
    | name=identifier dataType (SEQUENCE? BY REF)? (PATH string)? (DEFAULT expression)?
    ;

partitionExtensionClause
    : (PARTITION | SUBPARTITION) (LPAREN identifier RPAREN | FOR LPAREN expressionList RPAREN)
    ;

flashbackClause
    : AS OF (SCN expression | TIMESTAMP expression)
    ;

sampleClause
    : SAMPLE BLOCK? LPAREN expression RPAREN (SEED LPAREN expression RPAREN)?
    ;

joinRelation
    : NATURAL? joinType? JOIN relationPrimary pivotClause* matchRecognizeClause?
      joinPartitionClause? joinCriteria?
    | applyType relationPrimary pivotClause* matchRecognizeClause?
    ;

joinPartitionClause
    : PARTITION BY LPAREN expressionList RPAREN
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

windowClause
    : WINDOW namedWindow (COMMA namedWindow)*
    ;

namedWindow
    : name=identifier AS windowSpec
    ;

qualifyClause
    : QUALIFY expression
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
    : FETCH (APPROX | APPROXIMATE | EXACT)? (FIRST | NEXT) expression PERCENT_KEYWORD? (ROW | ROWS)?
      (ONLY | WITH TIES) vectorAccuracyClause?
    ;

vectorAccuracyClause
    : WITH TARGET ACCURACY (number | PARAMETERS LPAREN (EFSEARCH number
      | NEIGHBOR PARTITION PROBES number) RPAREN)
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
    | left=valueExpression (VECTOR_L2 | VECTOR_COSINE | VECTOR_INNER_PRODUCT) right=valueExpression
                                                                      #vectorOperatorExpression
    | left=valueExpression comparisonOperator right=valueExpression  #comparison
    ;

vectorMetric
    : COSINE | DOT_PRODUCT | EUCLIDEAN | EUCLIDEAN_SQUARED | L2_SQUARED | MANHATTAN | HAMMING | JACCARD
    ;

vectorConstructorOptions
    : COMMA (number | STAR) (COMMA vectorFormat (COMMA (DENSE | SPARSE | STAR))?)?
    ;

vectorFormat
    : INT8 | FLOAT32 | FLOAT64 | BINARY | STAR
    ;

comparisonOperator
    : EQ | NEQ | LT | GT | LTE | GTE
    ;

primaryExpression
    : CASE whenClause+ (ELSE elseExpr=expression)? END              #searchedCase
    | CASE operand=expression whenClause+ (ELSE elseExpr=expression)? END  #simpleCase
    | CAST LPAREN expression AS dataType RPAREN                      #castExpr
    | COLON (identifier | NUMBER_LITERAL)                           #bindExpression
    | VECTOR_DISTANCE LPAREN expression COMMA expression (COMMA vectorMetric)? RPAREN #vectorDistanceExpression
    | (TO_VECTOR | VECTOR) LPAREN expression vectorConstructorOptions? RPAREN          #vectorConstructorExpression
    | JSON_ARRAYAGG LPAREN expression (FORMAT JSON)? jsonAggregateOrderBy?
      jsonOnNullClause? jsonAggregateReturning? STRICT? RPAREN jsonAggregateFilter?
                                                                      #jsonArrayAggregateExpression
    | JSON_OBJECTAGG LPAREN KEY? expression (VALUE | COLON) expression (FORMAT JSON)?
      jsonOnNullClause? jsonAggregateReturning? STRICT? (WITH UNIQUE KEYS)? RPAREN jsonAggregateFilter?
                                                                      #jsonObjectAggregateExpression
    | functionName LPAREN STAR RPAREN nullTreatmentClause? keepClause? withinGroupClause? (OVER windowSpec)? #functionCallStar
    | functionName LPAREN setQuantifier? expressionList listaggOverflowClause? RPAREN nullTreatmentClause? keepClause? withinGroupClause? (OVER windowSpec)?  #functionCall
    | functionName LPAREN RPAREN nullTreatmentClause? keepClause? withinGroupClause? (OVER windowSpec)?      #functionCallEmpty
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

jsonAggregateOrderBy
    : ORDER BY sortItem (COMMA sortItem)*
    ;

jsonAggregateFilter
    : FILTER LPAREN WHERE expression RPAREN
    ;

jsonOnNullClause
    : (NULL | ABSENT) ON NULL
    ;

jsonAggregateReturning
    : RETURNING (VARCHAR2 (LPAREN number (BYTE | CHAR_KEYWORD)? RPAREN)? (WITH TYPENAME)?
                | (CLOB | BLOB) (REFERENCE | VALUE)?
                | JSON)
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

outerJoinMarker
    : LPAREN PLUS RPAREN
    ;

windowSpec
    : base=identifier
    | LPAREN base=identifier? (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
    ;

keepClause
    : KEEP LPAREN identifier (FIRST | LAST) ORDER BY sortItem (COMMA sortItem)* RPAREN
    ;

nullTreatmentClause
    : (IGNORE | RESPECT) NULLS
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
      partitionExtensionClause?
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
    : UPDATE multipartIdentifier partitionExtensionClause? tableAlias SET assignmentList fromUsingClause? whereClause? returningClause?
    ;

deleteStatement
    : DELETE FROM multipartIdentifier partitionExtensionClause? tableAlias fromUsingClause? whereClause? returningClause?
    ;

fromUsingClause
    : (FROM | USING) relationList
    ;

returningClause
    : (RETURN | RETURNING) returningExpression (COMMA returningExpression)*
      (BULK COLLECT)? INTO returningTarget (COMMA returningTarget)*
    ;

returningExpression
    : (OLD | NEW)? expression
    ;

returningTarget
    : COLON? identifier
    | COLON number
    ;

mergeStatement
    : MERGE INTO multipartIdentifier tableAlias
      USING (multipartIdentifier tableAlias | LPAREN query RPAREN tableAlias)
      ON expression
      mergeClause+ returningClause?
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
    | CREATE VECTOR INDEX multipartIdentifier ON multipartIdentifier LPAREN indexElementList RPAREN
      (INCLUDE LPAREN identifierList RPAREN)? GLOBAL?
      ORGANIZATION (INMEMORY NEIGHBOR? GRAPH | NEIGHBOR? PARTITIONS)
      vectorIndexOption* oracleIndexOption*
    ;

vectorIndexOption
    : WITH? DISTANCE vectorMetric
    | WITH TARGET ACCURACY number
    | QUANTIZATION SCALAR COMPRESSION RATIO number
    | PARAMETERS LPAREN TYPE (HNSW | IVF) (COMMA vectorIndexParameter)* RPAREN
    ;

vectorIndexParameter
    : (NEIGHBORS | EFCONSTRUCTION | SAMPLES_PER_PARTITION | MIN_VECTORS_PER_PARTITION) number
    | NEIGHBOR PARTITIONS number
    | NEIGHBOR PARTITION GROUPING (ON | OFF)
    | RESCORE FACTOR number
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

alterRoutineStatement
    : ALTER (PROCEDURE | FUNCTION | PACKAGE) multipartIdentifier .+?
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

principalStatement
    : CREATE (USER | ROLE) .+?
    | ALTER USER .+?
    | DROP (USER | ROLE) .+?
    ;

oracleSchemaObjectControlStatement
    : CREATE SEQUENCE multipartIdentifier .+?
    | ALTER SEQUENCE multipartIdentifier .+?
    | DROP SEQUENCE multipartIdentifier
    | CREATE (OR REPLACE)? TYPE multipartIdentifier (AS | IS) (OBJECT | TABLE) .+?
    | ALTER TYPE multipartIdentifier .+?
    | DROP TYPE multipartIdentifier (FORCE | VALIDATE)?
    | CREATE (OR REPLACE)? PUBLIC? SYNONYM multipartIdentifier FOR multipartIdentifier
    | DROP PUBLIC? SYNONYM multipartIdentifier
    | CREATE PUBLIC? DATABASE LINK multipartIdentifier .+?
    | DROP PUBLIC? DATABASE LINK multipartIdentifier
    | CREATE (OR REPLACE)? DIRECTORY multipartIdentifier AS string
    | DROP DIRECTORY multipartIdentifier
    | CREATE (BIGFILE | SMALLFILE)? TABLESPACE multipartIdentifier .+?
    | ALTER TABLESPACE multipartIdentifier .+?
    | DROP TABLESPACE multipartIdentifier .+?
    | CREATE (OR REPLACE)? CONTEXT multipartIdentifier .+?
    | DROP CONTEXT multipartIdentifier
    | CREATE PROFILE multipartIdentifier .+?
    | ALTER PROFILE multipartIdentifier .+?
    | DROP PROFILE multipartIdentifier .+?
    | CREATE AUDIT POLICY multipartIdentifier .+?
    | DROP AUDIT POLICY multipartIdentifier
    | AUDIT .+?
    | NOAUDIT .+?
    | domainControlStatement
    ;

domainControlStatement
    : CREATE USECASE? DOMAIN (IF NOT EXISTS)? multipartIdentifier AS domainDefinition
      (DISPLAY expression)? (ORDER expression)? domainAnnotations?
    | ALTER USECASE? DOMAIN (IF EXISTS)? multipartIdentifier
      ((ADD | MODIFY) (DISPLAY | ORDER) expression | DROP (DISPLAY | ORDER) | domainAnnotations)
    | DROP DOMAIN USECASE? (IF EXISTS)? multipartIdentifier (FORCE PRESERVE?)?
    ;

domainDefinition
    : (dataType | ENUM LPAREN domainEnumItem (COMMA domainEnumItem)* RPAREN) domainColumnOption*
    | LPAREN domainColumn (COMMA domainColumn)* RPAREN domainColumnOption*
    ;

domainColumn
    : identifier AS dataType STRICT? domainColumnOption*
    ;

domainEnumItem
    : identifier (EQ identifier)* (EQ expression)?
    ;

domainColumnOption
    : DEFAULT (ON NULL)? expression
    | (CONSTRAINT identifier)? (NOT? NULL | CHECK LPAREN expression RPAREN)
      (ENABLE | DISABLE)?
    | VALIDATE USING string
    | COLLATE identifier
    ;

domainAnnotations
    : ANNOTATIONS LPAREN domainAnnotation (COMMA domainAnnotation)* RPAREN
    ;

domainAnnotation
    : (ADD (IF NOT EXISTS | OR REPLACE)? | DROP (IF EXISTS)? | REPLACE)? identifier string?
    ;

flashbackTableStatement
    : FLASHBACK TABLE multipartIdentifier (COMMA multipartIdentifier)*
      TO flashbackTableTarget
    ;

flashbackTableTarget
    : BEFORE DROP (RENAME TO multipartIdentifier)?
    | SCN expression
    | TIMESTAMP expression
    | RESTORE POINT identifier
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

alterSystemStatement
    : ALTER SYSTEM (SET | RESET) .+?
    ;

alterMaterializedViewStatement
    : ALTER MATERIALIZED VIEW multipartIdentifier .+?
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME TO multipartIdentifier   #alterTableRename
    | ALTER TABLE multipartIdentifier ADD COLUMN? identifier dataType #alterTableAddColumn
    | ALTER TABLE multipartIdentifier ADD CONSTRAINT identifier PRIMARY KEY LPAREN identifierList RPAREN constraintState* #alterTableAddPrimaryKeyConstraint
    | ALTER TABLE multipartIdentifier ADD CONSTRAINT identifier FOREIGN KEY LPAREN localColumns=identifierList RPAREN REFERENCES refTable=multipartIdentifier (LPAREN refColumns=identifierList RPAREN)? constraintState* #alterTableAddForeignKeyConstraint
    | ALTER TABLE multipartIdentifier ADD CONSTRAINT identifier CHECK LPAREN expression RPAREN constraintState* #alterTableAddCheckConstraint
    | ALTER TABLE multipartIdentifier DROP CONSTRAINT identifier       #alterTableDropConstraint
    | ALTER TABLE target=multipartIdentifier EXCHANGE PARTITION identifier WITH TABLE source=multipartIdentifier exchangePartitionOption* #alterTableExchangePartition
    | ALTER TABLE multipartIdentifier alterTableAction                #alterTableOther
    ;

exchangePartitionOption
    : (INCLUDING | EXCLUDING) INDEXES
    | (WITH | WITHOUT) VALIDATION
    | UPDATE INDEXES
    ;

constraintState
    : (ENABLE | DISABLE) (VALIDATE | NOVALIDATE)?
    | (VALIDATE | NOVALIDATE)
    | NOT? DEFERRABLE
    | INITIALLY (IMMEDIATE | DEFERRED)
    | (RELY | NORELY)
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
    : VECTOR LPAREN (number | STAR) (COMMA vectorFormat (COMMA (DENSE | SPARSE | STAR))?)? RPAREN
    | identifier (LPAREN NUMBER_LITERAL (COMMA NUMBER_LITERAL)* RPAREN)?
    | identifier LT dataType (COMMA dataType)* GT
    ;

// ============ Common ============

multipartIdentifier
    : identifier (DOT identifier)* dbLinkSuffix?
    ;

dbLinkSuffix
    : AT qualifiedName
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
    : ADD | ANALYZE | APPLY | ASC | AUDIT | BITMAP | CASCADE | CAST | COLUMN | COLUMNS | COMMENT | COMPUTE | CONNECT_BY_ROOT | CONSTRAINT | CONSTRAINTS | CONTEXT | DEFAULT
    | BEFORE | BEGIN | BEQUEATH | BODY | BUILD | CURRENT_USER | DATABASE | DECLARE | DEFERRED | DEFERRABLE | DEFINER | DEMAND | DESCRIBE | DESC | DISABLE | DUAL | ENABLE | END | ERROR | EXISTS | EXPLAIN | EXTERNAL | FALSE
    | BULK | CHECK | COLLECT | COMPLETE | CONSTRAINT | EXCHANGE | EXCLUDING | FAST | FETCH | FIRST | FLASHBACK | FOR | FORCE | FOREIGN | FUNCTION | GRANT | IF | IMMEDIATE | INCLUDING | INDEX | INDEXES | INITIALLY | INTERVAL | LAST | LATERAL | LIKE | LIMIT | MATERIALIZED | MINUS_SET | NEXT | NO | NORELY | NOVALIDATE | NULL
    | DISTANCE | INMEMORY | GRAPH | HNSW | IVF | NEIGHBORS | EFCONSTRUCTION | SAMPLES_PER_PARTITION
    | MIN_VECTORS_PER_PARTITION | L2_SQUARED | QUANTIZATION | COMPRESSION | RATIO | SCALAR | RESCORE | FACTOR | OFF
    | JSON_ARRAYAGG | JSON_OBJECTAGG | ABSENT | KEYS | FILTER | VARCHAR2
    | CHAR_KEYWORD | BYTE | CLOB | BLOB | TYPENAME | REFERENCE
    | VECTOR_DISTANCE | TO_VECTOR | VECTOR | COSINE | DOT_PRODUCT | EUCLIDEAN | EUCLIDEAN_SQUARED
    | MANHATTAN | HAMMING | JACCARD | INT8 | FLOAT32 | FLOAT64 | BINARY | DENSE | SPARSE
    | APPROX | APPROXIMATE | EXACT | TARGET | ACCURACY | PARAMETERS | EFSEARCH | NEIGHBOR | PROBES
    | SEARCH | CYCLE | DEPTH | BREADTH
    | WINDOW | QUALIFY | OLD | NEW | NATURAL | DOMAIN | USECASE | ENUM | DISPLAY | MODIFY | STRICT | ANNOTATIONS | COLLATE
    | PACKAGE | PASSING | PATH | PERCENT_KEYWORD | REFRESH
    | KEY | NOAUDIT | NOCYCLE | NULLS | OBJECT | OF | OFFSET | ONLY | OPTION | OVER | PARTITION | PLAN | POLICY | PRIMARY | PRIVATE | PRIOR | PROCEDURE | PROFILE | PUBLIC | PURGE | READ | REFERENCES | ROLE | USER | WRITE | ISOLATION | LEVEL | SERIALIZABLE | RENAME | REPLACE | REUSE | ROW | ROWS
    | CURRENT | UNBOUNDED | PRECEDING | FOLLOWING
    | POINT | RESTORE | RETURN | REVOKE | ROLLBACK | ROLLUP | SAVEPOINT | SEQUENCE | SESSION | SYSTEM | TRANSACTION | RESET | SET | SETS | SHOW | SIBLINGS | START | STATISTICS | STORAGE | STRUCTURE | SYNONYM | TABLE | TEMPORARY | TO | TRIGGER | TRUE
    | TRUNCATE | TYPE | VALUES | VALIDATION | VIEW | DATE | DIRECTORY | ESTIMATE | LINK | NOWAIT | OVERFLOW | RELY | RETURNING | SCN | TIES | TIMESTAMP | UNIQUE | VALIDATE | WAIT | WITHIN | WITHOUT
    | SKIP_KEYWORD | LOCK | LOCKED | MODE | SHARE | EXCLUSIVE
    | PIVOT | UNPIVOT | MATCH_RECOGNIZE | MODEL | MEASURES | DIMENSION | RULES | UPSERT | UPDATED | IGNORE | RESPECT | KEEP | NAV
    | PATTERN | DEFINE | AFTER | MATCH | ONE | PER | PAST
    | BIGFILE | INCLUDE | EXCLUDE | XML | ARRAY | CONDITIONAL | UNCONDITIONAL | EMPTY | WRAPPER | XMLTABLE | XMLNAMESPACES | VALUE | REF | JSON | NESTED | ORDINALITY | SAMPLE | BLOCK | SEED | SMALLFILE | TABLESPACE | ONLINE | SUBPARTITION
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
