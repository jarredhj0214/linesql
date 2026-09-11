parser grammar FlinkParser;

options { tokenVocab = FlinkLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | hiveMultiInsertStatement                                       #hiveMultiInsertStmt
    | insertDirectoryStatement                                       #insertDirectoryStmt
    | insertStatement                                                #insertStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | mergeStatement                                                 #mergeStmt
    | loadDataStatement                                              #loadDataStmt
    | createMaterializedTableStatement                               #createMaterializedTableStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | createCatalogStatement                                         #createCatalogStmt
    | createDatabaseStatement                                        #createDatabaseStmt
    | createFunctionStatement                                        #createFunctionStmt
    | createModelStatement                                           #createModelStmt
    | dropTableStatement                                             #dropTableStmt
    | dropMaterializedTableStatement                                 #dropMaterializedTableStmt
    | dropViewStatement                                              #dropViewStmt
    | dropDatabaseStatement                                          #dropDatabaseStmt
    | dropFunctionStatement                                          #dropFunctionStmt
    | dropCatalogStatement                                           #dropCatalogStmt
    | dropModelStatement                                             #dropModelStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
    | alterMaterializedTableStatement                                #alterMaterializedTableStmt
    | alterViewStatement                                             #alterViewStmt
    | alterDatabaseStatement                                         #alterDatabaseStmt
    | alterFunctionStatement                                         #alterFunctionStmt
    | alterCatalogStatement                                          #alterCatalogStmt
    | alterModelStatement                                            #alterModelStmt
    | useStatement                                                   #useStmt
    | jobStatement                                                   #jobStmt
    | showStatement                                                  #showStmt
    | analyzeStatement                                               #analyzeStmt
    | describeStatement                                              #describeStmt
    | commentStatement                                               #commentStmt
    | explainStatement                                               #explainStmt
    | planStatement                                                  #planStmt
    | callStatement                                                  #callStmt
    | utilityStatement                                               #utilityStmt
    | executeStatementSet                                            #executeStmtSet
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
    ;

queryPrimary
    : querySpecification                                             #queryPrimaryDefault
    | VALUES valuesClause (COMMA valuesClause)*                      #valuesQuery
    | LPAREN query RPAREN                                            #subqueryPrimary
    ;

querySpecification
    : selectClause fromClause? whereClause? groupByClause? havingClause? qualifyClause? windowClause?
    ;

selectClause
    : SELECT setQuantifier? (transformClause | selectItemList)
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

transformClause
    : TRANSFORM LPAREN expressionList RPAREN USING string (AS transformOutputList)?
    ;

transformOutputList
    : transformOutput (COMMA transformOutput)*
    ;

transformOutput
    : identifier identifier?
    ;

fromClause
    : FROM relationList
    ;

relationList
    : relation (COMMA relation)*
    ;

relation
    : relationPrimary lateralViewClause* joinRelation*
    ;

relationPrimary
    : multipartIdentifier matchRecognizeClause tableAlias            #matchRecognizeRelation
    | multipartIdentifier temporalClause tableAlias                  #temporalTableName
    | multipartIdentifier tableSampleClause? tableAlias              #tableName
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    | functionName LPAREN tableFunctionArgList? RPAREN tableAlias    #bareTableFunction
    | LATERAL TABLE LPAREN functionName LPAREN tableFunctionArgList? RPAREN RPAREN tableAlias #lateralTableFunction
    | TABLE LPAREN functionName LPAREN tableFunctionArgList RPAREN RPAREN tableAlias  #tableFunction
    | UNNEST LPAREN expression RPAREN tableAlias                      #unnestTableFunction
    ;

joinRelation
    : NATURAL? joinType? JOIN relationPrimary temporalClause? joinCriteria?
    ;

lateralViewClause
    : LATERAL VIEW OUTER? functionName LPAREN expressionList? RPAREN strictIdentifier AS? identifierList
    ;

tableSampleClause
    : TABLESAMPLE LPAREN expression ROWS RPAREN
    ;

temporalClause
    : FOR SYSTEM_TIME AS OF expression
    ;

joinType
    : INNER
    | LEFT OUTER?
    | LEFT SEMI_JOIN
    | LEFT ANTI
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    | SEMI_JOIN
    | ANTI
    ;

joinCriteria
    : ON expression
    | USING LPAREN identifierList RPAREN
    ;

matchRecognizeClause
    : MATCH_RECOGNIZE LPAREN
      (PARTITION BY expressionList)?
      (ORDER BY sortItem (COMMA sortItem)*)?
      (MEASURES matchMeasure (COMMA matchMeasure)*)?
      ((ONE | ALL) (ROW | ROWS) PER MATCH)?
      afterMatchSkipClause?
      PATTERN LPAREN patternExpression RPAREN
      (WITHIN expression)?
      (DEFINE matchDefine (COMMA matchDefine)*)?
      RPAREN
    ;

afterMatchSkipClause
    : AFTER MATCH SKIP_KEYWORD PAST LAST ROW
    | AFTER MATCH SKIP_KEYWORD TO NEXT ROW
    | AFTER MATCH SKIP_KEYWORD TO (FIRST | LAST) identifier
    ;

matchMeasure
    : expression AS identifier
    ;

matchDefine
    : identifier AS expression
    ;

patternExpression
    : patternConcatenation (PIPE patternConcatenation)*
    ;

patternConcatenation
    : patternPrimary+
    ;

patternPrimary
    : identifier patternQuantifier?
    | LPAREN patternExpression RPAREN patternQuantifier?
    ;

patternQuantifier
    : (PLUS | STAR | QUESTION) QUESTION?
    | LBRACE number (COMMA number?)? RBRACE QUESTION?
    ;

tableAlias
    : (AS? strictIdentifier (LPAREN identifierList RPAREN)?)?
    ;

whereClause
    : WHERE expression
    ;

groupByClause
    : GROUP BY groupingElement (COMMA groupingElement)*
    ;

groupingElement
    : expression
    | ROLLUP LPAREN expressionList RPAREN
    | CUBE LPAREN expressionList RPAREN
    | GROUPING SETS LPAREN groupingSet (COMMA groupingSet)* RPAREN
    ;

groupingSet
    : LPAREN expressionList? RPAREN
    ;

havingClause
    : HAVING expression
    ;

qualifyClause
    : QUALIFY expression
    ;

queryOrganization
    : hiveQueryOrganization?
      (ORDER BY sortItem (COMMA sortItem)*)?
      (LIMIT ((expression COMMA expression) | expression | ALL))?
      (OFFSET expression (ROW | ROWS)?)?
      (FETCH (FIRST | NEXT) expression? (ROW | ROWS) ONLY)?
    ;

hiveQueryOrganization
    : DISTRIBUTE BY expressionList (SORT BY sortItem (COMMA sortItem)*)?
    | SORT BY sortItem (COMMA sortItem)*
    | CLUSTER BY expressionList
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
    | comparisonOperator (ALL | ANY | SOME) LPAREN query RPAREN
    | IS NOT? (NULL | TRUE | FALSE | UNKNOWN)
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
    | JSON_EXISTS LPAREN expression COMMA expression jsonExistsOnError? RPAREN #jsonExistsCall
    | JSON_VALUE LPAREN expression COMMA expression jsonReturning? jsonValueBehavior* RPAREN #jsonValueCall
    | JSON_QUERY LPAREN expression COMMA expression jsonReturning? jsonQueryWrapper? jsonQueryBehavior* RPAREN #jsonQueryCall
    | JSON_OBJECT LPAREN jsonObjectArgList? jsonOnNull? RPAREN       #jsonObjectCall
    | JSON_ARRAY LPAREN expressionList? jsonOnNull? RPAREN           #jsonArrayCall
    | JSON_OBJECTAGG LPAREN KEY? expression VALUE expression jsonOnNull? RPAREN filterClause? overClause? #jsonObjectAggCall
    | JSON_ARRAYAGG LPAREN expression jsonOnNull? RPAREN filterClause? overClause? #jsonArrayAggCall
    | functionName LPAREN expression TO timeIntervalUnit RPAREN      #timeFloorCeilCall
    | EXTRACT LPAREN timeIntervalUnit FROM expression RPAREN         #extractCall
    | functionName LPAREN trimSpecification? expression FROM expression RPAREN #trimCall
    | functionName LPAREN expression IN expression RPAREN            #positionCall
    | functionName LPAREN STAR RPAREN filterClause? overClause?      #functionCallStar
    | functionName LPAREN setQuantifier? expressionList nullTreatment? RPAREN filterClause? overClause?  #functionCall
    | functionName LPAREN RPAREN filterClause? overClause?           #functionCallEmpty
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | primaryExpression DOT identifier                                #dereference
    | primaryExpression LBRACKET expression RBRACKET                 #subscriptExpression
    | identifier                                                     #columnReference
    | number                                                         #numberLiteral
    | string                                                         #stringLiteral
    | NULL                                                           #nullLiteral
    | TRUE                                                           #booleanTrue
    | FALSE                                                          #booleanFalse
    | INTERVAL expression identifier                                 #intervalLiteral
    | identifier string                                              #typedStringLiteral
    | mapLiteral                                                     #mapExpression
    | DEFAULT                                                        #defaultLiteral
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

jsonObjectArg
    : KEY? expression VALUE expression
    ;

jsonObjectArgList
    : jsonObjectArg (COMMA jsonObjectArg)*
    ;

jsonOnNull
    : (NULL | ABSENT) ON NULL
    ;

jsonReturning
    : RETURNING dataType
    ;

jsonValueBehavior
    : (NULL | ERROR | DEFAULT expression) ON (EMPTY | ERROR)
    ;

jsonQueryWrapper
    : WITHOUT ARRAY? WRAPPER
    | WITH (CONDITIONAL | UNCONDITIONAL)? ARRAY? WRAPPER
    ;

jsonQueryBehavior
    : (NULL | ERROR | EMPTY (ARRAY | OBJECT)) ON (EMPTY | ERROR)
    ;

jsonExistsOnError
    : (TRUE | FALSE | UNKNOWN | ERROR) ON ERROR
    ;

timeIntervalUnit
    : identifier
    | EPOCH
    ;

trimSpecification
    : identifier
    ;

windowSpec
    : LPAREN (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
    ;

overClause
    : OVER (windowSpec | identifier)
    ;

filterClause
    : FILTER LPAREN WHERE expression RPAREN
    ;

nullTreatment
    : (RESPECT | IGNORE) NULLS
    ;

windowClause
    : WINDOW namedWindow (COMMA namedWindow)*
    ;

namedWindow
    : identifier AS windowSpec
    ;

windowFrame
    : (ROWS | RANGE) BETWEEN frameBound AND frameBound
    | (ROWS | RANGE) frameBound
    ;

frameBound
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
    | IF
    | CAST
    | EXTRACT
    | VALUES
    | DEFAULT
    | LAST
    ;

expressionList
    : expression (COMMA expression)*
    ;

// ============ DML Statements ============

insertStatement
    : ctes? EXECUTE? INSERT ((INTO TABLE?) | (OVERWRITE TABLE?)) multipartIdentifier
      (PARTITION partitionSpec (IF NOT existsKeyword)?)?
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)*)
      onConflictClause?
    ;

hiveMultiInsertStatement
    : FROM relation hiveInsertBranch (COMMA? hiveInsertBranch)*
    ;

hiveInsertBranch
    : INSERT ((INTO TABLE?) | (OVERWRITE TABLE?)) multipartIdentifier
      (PARTITION partitionSpec (IF NOT existsKeyword)?)?
      (LPAREN columnList=identifierList RPAREN)?
      querySpecification
    ;

insertDirectoryStatement
    : INSERT OVERWRITE LOCAL? DIRECTORY string hiveDirectoryOutputClause* (USING identifier)? query
    ;

loadDataStatement
    : LOAD DATA LOCAL? INPATH string OVERWRITE? INTO TABLE multipartIdentifier (PARTITION partitionSpec)?
    ;

onConflictClause
    : ON CONFLICT DO (NOTHING | ERROR | DEDUPLICATE)
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

updateStatement
    : UPDATE multipartIdentifier SET assignmentList whereClause?
    ;

deleteStatement
    : DELETE FROM multipartIdentifier whereClause?
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : multipartIdentifier EQ expression
    ;

// ============ TVF Arguments ============

tableFunctionArgList
    : tableFunctionArg (COMMA tableFunctionArg)*
    ;

tableFunctionArg
    : identifier FAT_ARROW tableFunctionArg                          #namedTableFunctionArg
    | TABLE multipartIdentifier                                      #tvfTableArg
    | MODEL multipartIdentifier                                      #tvfModelArg
    | DESCRIPTOR LPAREN identifier RPAREN                            #tvfDescriptorArg
    | expression                                                     #tvfExprArg
    ;

mapLiteral
    : MAP LBRACKET expressionList? RBRACKET
    ;

// ============ MERGE Statement ============

mergeStatement
    : MERGE INTO multipartIdentifier tableAlias
      USING (multipartIdentifier tableAlias | LPAREN query RPAREN tableAlias)
      ON expression
      mergeClause+
    ;

mergeClause
    : WHEN MATCHED (AND expression)? THEN mergeMatchedAction
    | WHEN NOT MATCHED (AND expression)? THEN mergeNotMatchedAction
    ;

mergeMatchedAction
    : UPDATE SET assignmentList
    | DELETE
    ;

mergeNotMatchedAction
    : INSERT (LPAREN identifierList RPAREN)? VALUES LPAREN expressionList RPAREN
    ;

// ============ DDL Statements ============

createTableStatement
    : CREATE (TEMPORARY | EXTERNAL)* TABLE (IF NOT existsKeyword)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      distributionClause?
      partitionedByClause?
      hiveCreateTableClause*
      ((WITH LPAREN propertyList RPAREN)? (likeClause | AS query)?
      | likeClause (WITH LPAREN propertyList RPAREN)?)
    | CREATE (OR REPLACE)? TABLE (IF NOT existsKeyword)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      distributionClause?
      partitionedByClause?
      hiveCreateTableClause*
      (WITH LPAREN propertyList RPAREN)?
      AS query
    | REPLACE TABLE multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      distributionClause?
      (WITH LPAREN propertyList RPAREN)?
      AS query
    ;

createViewStatement
    : CREATE TEMPORARY? (OR REPLACE)? VIEW (IF NOT existsKeyword)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      commentClause?
      AS query
    ;

createMaterializedTableStatement
    : CREATE MATERIALIZED TABLE (IF NOT existsKeyword)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      distributionClause?
      partitionedByClause?
      (WITH LPAREN propertyList RPAREN)?
      (FRESHNESS EQ expression)?
      (REFRESH_MODE EQ identifier)?
      (AS query)?
    | CREATE OR ALTER MATERIALIZED TABLE (IF NOT existsKeyword)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      distributionClause?
      partitionedByClause?
      (WITH LPAREN propertyList RPAREN)?
      (FRESHNESS EQ expression)?
      (REFRESH_MODE EQ identifier)?
      (AS query)?
    ;

createCatalogStatement
    : CREATE CATALOG (IF NOT existsKeyword)? identifier commentClause? WITH LPAREN propertyList RPAREN
    ;

createDatabaseStatement
    : CREATE (DATABASE | SCHEMA) (IF NOT existsKeyword)? multipartIdentifier commentClause? (LOCATION string)?
      (WITH LPAREN propertyList RPAREN | WITH? DBPROPERTIES LPAREN propertyList RPAREN)?
    ;

createFunctionStatement
    : CREATE (TEMPORARY SYSTEM?)? FUNCTION (IF NOT existsKeyword)? multipartIdentifier
      AS functionIdentifier (LANGUAGE functionLanguage)? functionUsingClause? (WITH LPAREN propertyList RPAREN)?
    ;

functionIdentifier
    : string
    | multipartIdentifier
    ;

createModelStatement
    : CREATE TEMPORARY? MODEL (IF NOT existsKeyword)? multipartIdentifier
      (modelSignatureClause+ | LPAREN tableElementList RPAREN)?
      commentClause?
      (WITH LPAREN propertyList RPAREN)?
    ;

modelSignatureClause
    : INPUT LPAREN tableElementList RPAREN
    | OUTPUT LPAREN tableElementList RPAREN
    ;

dropTableStatement
    : DROP TEMPORARY? TABLE (IF existsKeyword)? multipartIdentifier PURGE?
    ;

dropViewStatement
    : DROP TEMPORARY? VIEW (IF existsKeyword)? multipartIdentifier
    ;

dropDatabaseStatement
    : DROP DATABASE (IF existsKeyword)? multipartIdentifier (RESTRICT | CASCADE)?
    ;

dropFunctionStatement
    : DROP (TEMPORARY SYSTEM?)? FUNCTION (IF existsKeyword)? multipartIdentifier
    ;

dropCatalogStatement
    : DROP CATALOG (IF existsKeyword)? identifier
    ;

dropModelStatement
    : DROP TEMPORARY? MODEL (IF existsKeyword)? multipartIdentifier
    ;

dropMaterializedTableStatement
    : DROP MATERIALIZED TABLE (IF existsKeyword)? multipartIdentifier
    ;

truncateTableStatement
    : TRUNCATE TABLE? multipartIdentifier (PARTITION partitionSpec)?
    ;

alterTableStatement
    : ALTER TABLE (IF existsKeyword)? multipartIdentifier RENAME (TO | AS)? multipartIdentifier   #alterTableRename
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier RENAME identifier TO identifier         #alterTableRenameColumn
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier ADD COLUMN? tableElement columnPosition? #alterTableAddColumn
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier CHANGE COLUMN? identifier tableElement columnPosition? (CASCADE | RESTRICT)? #alterTableChangeColumn
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier ADD CONSTRAINT identifier PRIMARY KEY LPAREN identifierList RPAREN NOT ENFORCED #alterTableAddConstraint
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier ADD COLUMNS LPAREN tableElementList RPAREN (CASCADE | RESTRICT)? #alterTableAddHiveColumns
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier ADD LPAREN alterTableElementList RPAREN #alterTableAddColumns
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier REPLACE COLUMNS LPAREN tableElementList RPAREN (CASCADE | RESTRICT)? #alterTableReplaceColumns
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier MODIFY COLUMN? tableElement columnPosition? #alterTableModifyColumn
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier MODIFY LPAREN alterTableElementList RPAREN #alterTableModifyColumns
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier DROP COLUMN? identifier                 #alterTableDropColumn
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier DROP LPAREN identifierList RPAREN       #alterTableDropColumns
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier ADD (IF NOT existsKeyword)? PARTITION partitionSpec locationClause? (WITH LPAREN propertyList RPAREN)?
      (PARTITION partitionSpec locationClause? (WITH LPAREN propertyList RPAREN)?)* #alterTableAddPartition
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier DROP (IF existsKeyword)? PARTITION partitionSpec (COMMA PARTITION partitionSpec)* #alterTableDropPartition
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier PARTITION partitionSpec RENAME TO PARTITION partitionSpec #alterTableRenamePartition
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier PARTITION partitionSpec SET LOCATION string #alterTablePartitionSetLocation
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier PARTITION partitionSpec SET FILEFORMAT hiveFileFormat #alterTablePartitionSetFileFormat
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier ADD (distributionClause | alterDistributionClause) #alterTableAddDistribution
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier DROP DISTRIBUTION                       #alterTableDropDistribution
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier DROP PRIMARY KEY                        #alterTableDropPrimaryKey
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier DROP CONSTRAINT identifier              #alterTableDropConstraint
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier DROP WATERMARK                          #alterTableDropWatermark
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier RESET LPAREN stringList RPAREN          #alterTableResetProperties
    | ALTER TABLE (IF existsKeyword)? multipartIdentifier alterTableAction                        #alterTableOther
    ;

alterTableAction
    : SET LPAREN propertyList RPAREN
    | SET TBLPROPERTIES LPAREN propertyList RPAREN
    | SET SERDE string (WITH SERDEPROPERTIES LPAREN propertyList RPAREN)?
    | SET SERDEPROPERTIES LPAREN propertyList RPAREN
    | UNSET SERDEPROPERTIES LPAREN stringList RPAREN
    | SET LOCATION string
    | SET FILEFORMAT hiveFileFormat
    | COMMENT EQ? string
    | .+?
    ;

alterViewStatement
    : ALTER VIEW multipartIdentifier RENAME TO multipartIdentifier            #alterViewRename
    | ALTER VIEW multipartIdentifier AS query                                  #alterViewAsQuery
    ;

alterDatabaseStatement
    : ALTER DATABASE multipartIdentifier SET (LPAREN propertyList RPAREN | DBPROPERTIES LPAREN propertyList RPAREN | LOCATION string)
    ;

alterFunctionStatement
    : ALTER (TEMPORARY SYSTEM?)? FUNCTION (IF existsKeyword)? multipartIdentifier
      AS functionIdentifier (LANGUAGE functionLanguage)?
    ;

alterCatalogStatement
    : ALTER CATALOG identifier
      (SET LPAREN propertyList RPAREN
      | RESET LPAREN stringList RPAREN
      | COMMENT string)
    ;

alterModelStatement
    : ALTER MODEL (IF existsKeyword)? multipartIdentifier
      (SET LPAREN propertyList RPAREN
      | RESET LPAREN stringList RPAREN
      | RENAME TO multipartIdentifier)
    ;

alterMaterializedTableStatement
    : ALTER MATERIALIZED TABLE multipartIdentifier materializedTableAction
    ;

materializedTableAction
    : ADD COLUMN? tableElement columnPosition?
    | MODIFY COLUMN? tableElement columnPosition?
    | DROP COLUMN? identifier
    | SUSPEND
    | RESUME (WITH LPAREN propertyList RPAREN)?
    | REFRESH (PARTITION partitionSpec)?
    | SET LPAREN propertyList RPAREN
    | RESET LPAREN stringList RPAREN
    | AS query
    ;

useStatement
    : USE CATALOG identifier
    | USE (DATABASE)? multipartIdentifier
    ;

showStatement
    : SHOW CATALOGS showLikeClause?
    | SHOW CURRENT (CATALOG | DATABASE)
    | SHOW CREATE CATALOG identifier
    | SHOW DATABASES ((FROM | IN) identifier)? showLikeClause?
    | SHOW TABLES ((FROM | IN) multipartIdentifier)? showLikeClause?
    | SHOW CREATE TABLE multipartIdentifier
    | SHOW COLUMNS (FROM | IN) multipartIdentifier showLikeClause?
    | SHOW PARTITIONS multipartIdentifier (PARTITION partitionSpec)?
    | SHOW PROCEDURES ((FROM | IN) multipartIdentifier)? showLikeClause?
    | SHOW VIEWS ((FROM | IN) multipartIdentifier)? showLikeClause?
    | SHOW CREATE VIEW multipartIdentifier
    | SHOW MATERIALIZED TABLES ((FROM | IN) multipartIdentifier)? showLikeClause?
    | SHOW CREATE (OR ALTER)? MATERIALIZED TABLE multipartIdentifier
    | SHOW USER? FUNCTIONS ((FROM | IN) multipartIdentifier)? showLikeClause?
    | SHOW CREATE FUNCTION multipartIdentifier
    | SHOW FULL? MODULES
    | SHOW JARS
    | SHOW JOBS
    | SHOW MODELS ((FROM | IN) multipartIdentifier)? showLikeClause?
    | SHOW CREATE MODEL multipartIdentifier
    | SHOW .+?
    ;

showLikeClause
    : NOT? (LIKE | ILIKE) string
    ;

describeStatement
    : (DESCRIBE | DESC) TABLE? EXTENDED? multipartIdentifier
    | (DESCRIBE | DESC) CATALOG EXTENDED? identifier
    | (DESCRIBE | DESC) FUNCTION EXTENDED? multipartIdentifier
    | (DESCRIBE | DESC) MODEL EXTENDED? multipartIdentifier
    | (DESCRIBE | DESC) .+?
    ;

analyzeStatement
    : ANALYZE TABLE multipartIdentifier (PARTITION partitionSpec)? COMPUTE STATISTICS
      (FOR (ALL COLUMNS | COLUMNS identifierList))?
    ;

commentStatement
    : COMMENT ON (TABLE multipartIdentifier | COLUMN multipartIdentifier)
      IS string
    ;

explainStatement
    : EXPLAIN (PLAN FOR)? statement
    | EXPLAIN explainDetailList statement
    | EXPLAIN .+?
    ;

explainDetailList
    : identifier (COMMA identifier)*
    ;

planStatement
    : COMPILE PLAN string? FOR statement                              #compilePlanStatement
    | EXECUTE PLAN string                                             #executePlanStatement
    ;

utilityStatement
    : LOAD MODULE identifier (WITH LPAREN propertyList RPAREN)?
    | UNLOAD MODULE identifier
    | USE MODULES identifierList
    | ADD JAR string
    | REMOVE JAR string
    | HELP
    | QUIT
    | EXIT
    | CLEAR
    | SET (setAssignment | string EQ string | LPAREN property RPAREN)?
    | RESET (configKey | string | LPAREN (configKey | string) RPAREN)?
    | SHOW (CURRENT (CATALOG | DATABASE) | CATALOGS | DATABASES | TABLES | VIEWS | FUNCTIONS | MODULES | JARS)
    | SHOW CREATE (TABLE | VIEW | FUNCTION) multipartIdentifier
    ;

callStatement
    : CALL multipartIdentifier LPAREN expressionList? RPAREN
    ;

setAssignment
    : configKey EQ configValue
    ;

configKey
    : configKeyPart ((DOT | MINUS) configKeyPart)*
    ;

configKeyPart
    : identifier
    | NOT
    | NULL
    | TRUE
    | FALSE
    | number
    ;

configValue
    : configValuePart+
    ;

configValuePart
    : identifier
    | DROP
    | NOT
    | NULL
    | TRUE
    | FALSE
    | number
    | string
    | DOT
    | MINUS
    | COLON
    | SLASH
    | PERCENT
    ;

jobStatement
    : SHOW JOBS
    | (DESCRIBE | DESC) JOB string
    | STOP JOB string (WITH SAVEPOINT)? (WITH DRAIN)?
    ;

// ============ STATEMENT SET ============

executeStatementSet
    : EXECUTE? STATEMENT SET BEGIN (insertStatement SEMI)+ END
    ;

// ============ DDL Helpers ============

tableElementList
    : tableElement (COMMA tableElement)*
    ;

alterTableElementList
    : alterTableElement (COMMA alterTableElement)*
    ;

alterTableElement
    : tableElement columnPosition?
    ;

tableElement
    : identifier dataType metadataClause? columnConstraint*           #columnDefinition
    | identifier                                                      #columnNameOnlyDefinition
    | identifier AS expression commentClause?                         #computedColumnDefinition
    | WATERMARK FOR identifier AS expression                         #watermarkDefinition
    | (CONSTRAINT identifier)? PRIMARY KEY LPAREN identifierList RPAREN NOT ENFORCED #primaryKeyDefinition
    ;

columnConstraint
    : NOT NULL
    | NULL
    | COMMENT string
    | DEFAULT expression
    | (CONSTRAINT identifier)? PRIMARY KEY NOT ENFORCED
    ;

metadataClause
    : METADATA (FROM string)? VIRTUAL?
    ;

columnPosition
    : FIRST
    | AFTER identifier
    ;

commentClause
    : COMMENT string
    ;

propertyList
    : property (COMMA? property)*
    ;

property
    : string EQ string
    ;

likeClause
    : LIKE source=multipartIdentifier (LPAREN likeOption (COMMA? likeOption)* RPAREN)?
    ;

likeOption
    : (INCLUDING | EXCLUDING | OVERWRITING) identifier
    | (INCLUDING | EXCLUDING | OVERWRITING) OPTIONS
    ;

partitionedByClause
    : PARTITIONED BY LPAREN tableElementList RPAREN
    ;

hiveCreateTableClause
    : ROW FORMAT hiveRowFormat
    | STORED AS hiveFileFormat
    | locationClause
    | TBLPROPERTIES LPAREN propertyList RPAREN
    ;

locationClause
    : LOCATION string
    ;

hiveRowFormat
    : DELIMITED hiveDelimitedRowFormatItem*
    | SERDE string (WITH SERDEPROPERTIES LPAREN propertyList RPAREN)?
    ;

hiveDelimitedRowFormatItem
    : FIELDS TERMINATED BY string
    | COLLECTION ITEMS TERMINATED BY string
    | MAP KEYS TERMINATED BY string
    | LINES TERMINATED BY string
    | NULL DEFINED AS string
    | ESCAPED BY string
    ;

hiveFileFormat
    : identifier
    | INPUTFORMAT string OUTPUTFORMAT string
    ;

hiveDirectoryOutputClause
    : ROW FORMAT hiveRowFormat
    | STORED AS hiveFileFormat
    ;

distributionClause
    : DISTRIBUTED BY ((HASH | RANGE) LPAREN identifierList RPAREN | LPAREN identifierList RPAREN) (INTO number BUCKETS?)?
    | DISTRIBUTED INTO number BUCKETS?
    ;

alterDistributionClause
    : DISTRIBUTION BY ((HASH | RANGE) LPAREN identifierList RPAREN | LPAREN identifierList RPAREN) (INTO number BUCKETS?)?
    ;

partitionSpec
    : LPAREN partitionSpecItem (COMMA partitionSpecItem)* RPAREN
    ;

partitionSpecItem
    : identifier (EQ expression)?
    ;

stringList
    : string (COMMA string)*
    ;

existsKeyword
    : EXISTS
    | EXIST
    ;

functionLanguage
    : JAVA
    | SCALA
    | PYTHON
    ;

functionUsingClause
    : USING functionUsingItem (COMMA functionUsingItem)*
    ;

functionUsingItem
    : (JAR | ARTIFACT)? string
    ;

dataType
    : identifier (LPAREN NUMBER_LITERAL (COMMA NUMBER_LITERAL)* RPAREN)?
    | identifier LT dataType (COMMA dataType)* GT
    ;

// ============ Common ============

multipartIdentifier
    : identifier (DOT identifier)*
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
    : ABSENT | ADD | AFTER | ALL | ALWAYS | ANALYZE | ANTI | ANY | ARRAY | ARTIFACT | ASC | BEGIN | BUCKET | BUCKETS | CALL | CASCADE
    | CATALOG | CATALOGS | CAST | CHANGE | CLUSTER | COLLECTION | COLUMN | COLUMNS | COMMENT | COMPILE | COMPUTE | CONDITIONAL | CONFLICT | CONNECTOR | CONSTRAINT | CONTINUOUS
    | CUMULATE | CUBE | CURRENT | DATA | DATABASE | DATABASES | DAY | DBPROPERTIES | DEDUPLICATE | DEFAULT | DEFINE | DEFINED | DELIMITED | DESCRIBE | DESC | DESCRIPTOR | DIRECTORY | DISTRIBUTE | DISTRIBUTED
    | DISTRIBUTION | DO | DRAIN | EMPTY | END | ENFORCED | EPOCH | ERROR | ESCAPED | EXCLUDING | EXIST | EXISTS | EXECUTE | EXPLAIN | EXTRACT | EXTENDED
    | EXTERNAL | FALSE | FETCH | FIELDS | FILEFORMAT | FILTER | FIRST | FOLLOWING | FOR | FORMAT | FRESHNESS | FULL | FUNCTION | FUNCTIONS
    | GENERATED | GROUPING | HASH | HELP | HOP | HOUR | IF | ILIKE | INCLUDING | INTERVAL | JAVA
    | IGNORE | INPATH | INPUT | INPUTFORMAT | ITEMS | JAR | JARS | JSON_ARRAY | JSON_ARRAYAGG | JSON_EXISTS | JSON_OBJECT | JSON_OBJECTAGG | JSON_QUERY | JSON_VALUE | KEY | KEYS | LANGUAGE | LAST | LATERAL | LIKE | LIMIT | LINES | LOAD | LOCAL | LOCATION | MATCH | MATCHED
    | MAP | MATCH_RECOGNIZE | MATERIALIZED | MEASURES | MERGE | METADATA | MINUTE | MODEL | MODELS | MODIFY | MODULE | MODULES | NATURAL | NEXT | NOTHING | NULL | NULLS | OBJECT | OF | ONE | ONLY
    | OFFSET | OPTIONS | OUTPUT | OUTPUTFORMAT | OVER | OVERWRITE | OVERWRITING | PARTITION | PARTITIONED | PLAN
    | PAST | PATTERN | PER | PRECEDING | PERIOD | PRIMARY | PURGE | PYTHON | QUALIFY | RANGE | REFRESH | REFRESH_MODE | RENAME | REPLACE | RESET | RESPECT | RETURNING
    | QUIT | REMOVE | RESUME | RESTRICT | ROLLUP | ROW | ROWS | SAVEPOINT | SCALA | SCALAR | SCHEMA | SECOND | SEMI_JOIN | SERDE | SERDEPROPERTIES | SESSION | SET | SETS | SHOW | SKIP_KEYWORD | SOME | SORT | STATEMENT | STOP | STORED
    | STATISTICS | SUSPEND | SYS | SYSTEM | TABLE | TABLES | TABLESAMPLE | TBLPROPERTIES | TEMPORARY | TERMINATED | TIMESTAMP | TO | TRANSFORM | TRUE | TRUNCATE | TUMBLE
    | CLEAR | EXIT | UNBOUNDED | UNCONDITIONAL | UNKNOWN | UNLOAD | UNNEST | UNSET | USE | USER | VALUE | VALUES | VIEW | VIEWS | VIRTUAL | WATERMARK | WINDOW | WITHIN | WITHOUT | WRAPPER
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
