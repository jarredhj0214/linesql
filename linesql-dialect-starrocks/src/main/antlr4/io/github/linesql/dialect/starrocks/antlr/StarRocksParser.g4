parser grammar StarRocksParser;

options { tokenVocab = StarRocksLineageLexer; }

singleStatement
    : statement SEMI? VERTICAL_OUTPUT? EOF
    ;

statement
    : query                                                          #statementDefault
    | explainStatement                                               #explainStmt
    | submitTaskStatement                                            #submitTaskStmt
    | alterTaskStatement                                             #alterTaskStmt
    | dropTaskStatement                                              #dropTaskStmt
    | adminShowStatement                                             #adminShowStmt
    | adminRepairStatement                                           #adminRepairStmt
    | adminCheckTabletStatement                                      #adminCheckTabletStmt
    | adminSetPartitionVersionStatement                              #adminSetPartitionVersionStmt
    | adminStatement                                                 #adminStmt
    | insertStatement                                                #insertStmt
    | exportTableStatement                                           #exportTableStmt
    | loadLabelStatement                                             #loadLabelStmt
    | alterLoadStatement                                             #alterLoadStmt
    | alterSystemStatement                                           #alterSystemStmt
    | cancelDecommissionStatement                                    #cancelDecommissionStmt
    | backendBlacklistStatement                                      #backendBlacklistStmt
    | sqlBlacklistStatement                                          #sqlBlacklistStmt
    | syncStatement                                                  #syncStmt
    | createRoutineLoadStatement                                     #createRoutineLoadStmt
    | alterRoutineLoadStatement                                      #alterRoutineLoadStmt
    | routineLoadControlStatement                                    #routineLoadControlStmt
    | cancelRefreshMaterializedViewStatement                         #cancelRefreshMaterializedViewStmt
    | cancelAlterTableStatement                                      #cancelAlterTableStmt
    | cancelExportStatement                                          #cancelExportStmt
    | cancelLoadStatement                                            #cancelLoadStmt
    | createPipeStatement                                            #createPipeStmt
    | alterPipeStatement                                             #alterPipeStmt
    | dropPipeStatement                                              #dropPipeStmt
    | createAnalyzeStatement                                         #createAnalyzeStmt
    | dropAnalyzeStatement                                           #dropAnalyzeStmt
    | dropStatsStatement                                             #dropStatsStmt
    | killAnalyzeStatement                                           #killAnalyzeStmt
    | killStatement                                                  #killStmt
    | accountControlStatement                                        #accountControlStmt
    | backupStatement                                                #backupStmt
    | recoverStatement                                               #recoverStmt
    | restoreStatement                                               #restoreStmt
    | repositoryStatement                                            #repositoryStmt
    | fileStatement                                                  #fileStmt
    | catalogStatement                                               #catalogStmt
    | storageVolumeStatement                                         #storageVolumeStmt
    | warehouseStatement                                             #warehouseStmt
    | resourceGroupStatement                                         #resourceGroupStmt
    | resourceStatement                                              #resourceStmt
    | createFunctionStatement                                        #createFunctionStmt
    | dropFunctionStatement                                          #dropFunctionStmt
    | createDictionaryStatement                                      #createDictionaryStmt
    | refreshDictionaryStatement                                     #refreshDictionaryStmt
    | cancelRefreshDictionaryStatement                               #cancelRefreshDictionaryStmt
    | dropDictionaryStatement                                        #dropDictionaryStmt
    | mergeStatement                                                 #mergeStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | createIndexStatement                                           #createIndexStmt
    | createDatabaseStatement                                        #createDatabaseStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropIndexStatement                                             #dropIndexStmt
    | dropDatabaseStatement                                          #dropDatabaseStmt
    | dropTableStatement                                             #dropTableStmt
    | alterViewStatement                                             #alterViewStmt
    | dropViewStatement                                              #dropViewStmt
    | truncateTableStatement                                         #truncateTableStmt
    | refreshExternalTableStatement                                  #refreshExternalTableStmt
    | refreshMaterializedViewStatement                               #refreshMaterializedViewStmt
    | alterMaterializedViewStatement                                 #alterMaterializedViewStmt
    | alterDatabaseStatement                                         #alterDatabaseStmt
    | alterTableStatement                                            #alterTableStmt
    | analyzeTableStatement                                          #analyzeTableStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | transactionControlStatement                                    #transactionControlStmt
    | preparedStatement                                              #preparedStmt
    | useStatement                                                   #useStmt
    | setCatalogStatement                                            #setCatalogStmt
    | setStatement                                                   #setStmt
    | commentStatement                                               #commentStmt
    ;

// ============ Query ============

query
    : ctes? queryTerm queryOrganization intoOutfileClause?
    ;

ctes
    : WITH RECURSIVE? namedQuery (COMMA namedQuery)*
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
    | MINUS_KW DISTINCT?
    | INTERSECT (ALL | DISTINCT)?
    ;

queryPrimary
    : querySpecification                                             #queryPrimaryDefault
    | LPAREN query RPAREN                                            #subqueryPrimary
    ;

querySpecification
    : selectClause fromClause? whereClause? groupByClause? havingClause? qualifyClause?
    ;

selectClause
    : SELECT setQuantifier? selectItemList
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
    | qualifiedName DOT STAR excludeClause?                          #selectQualifiedStar
    | STAR excludeClause?                                            #selectStar
    ;

excludeClause
    : EXCLUDE LPAREN identifierList RPAREN
    ;

fromClause
    : FROM relationList
    ;

relationList
    : relation (COMMA relation)*
    ;

relation
    : relationPrimary pivotClause* joinRelation*
    ;

pivotClause
    : PIVOT LPAREN pivotAggregate (COMMA pivotAggregate)* FOR pivotColumn IN LPAREN pivotValueList RPAREN RPAREN
    ;

pivotAggregate
    : functionName LPAREN setQuantifier? expressionList? RPAREN (AS? identifier)?
    ;

pivotColumn
    : identifier
    | LPAREN identifierList RPAREN
    ;

pivotValueList
    : pivotValue (COMMA pivotValue)*
    ;

pivotValue
    : expression
    | LPAREN expressionList RPAREN
    ;

relationPrimary
    : FILES LPAREN propertyList RPAREN tableAlias                    #filesTableFunction
    | LATERAL? identifier LPAREN (expressionList | propertyList)? RPAREN tableAlias            #tableFunction
    | multipartIdentifier tableVersionClause? tableHintClause? partitionClause? tableAlias                 #tableName
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    ;

joinRelation
    : joinType? JOIN joinHint? relationPrimary joinCriteria?
    ;

joinHint
    : LBRACKET identifier RBRACKET
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    | LEFT? SEMI_JOIN
    | LEFT? ANTI
    | ASOF
    ;

joinCriteria
    : ON expression
    | USING LPAREN identifierList RPAREN
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
    : (ORDER BY sortItem (COMMA sortItem)*)?
      (LIMIT expression (COMMA expression)?)?
      (OFFSET expression)?
    ;

sortItem
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

intoOutfileClause
    : INTO OUTFILE string (FORMAT AS identifier)? propertiesClause?
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
    | NOT? LIKE valueExpression (ESCAPE valueExpression)?
    | NOT? (MATCH | MATCH_ANY | MATCH_ALL) valueExpression
    | NOT? (REGEXP | RLIKE) valueExpression
    | IS NOT? (NULL | TRUE | FALSE)
    | comparisonOperator (ALL | ANY | SOME) LPAREN query RPAREN
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
    | functionName LPAREN STAR RPAREN functionFilterClause? (OVER windowSpec)?             #functionCallStar
    | functionName LPAREN setQuantifier? expressionList RPAREN functionFilterClause? (OVER windowSpec)?  #functionCall
    | functionName LPAREN RPAREN functionFilterClause? (OVER windowSpec)?                  #functionCallEmpty
    | identifier ARROW expression                                   #lambdaExpression
    | LPAREN identifierList RPAREN ARROW expression                  #lambdaExpressionList
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression COMMA expressionList RPAREN                  #rowExpression
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | LBRACKET expressionList? RBRACKET                              #arrayLiteral
    | identifier LBRACE mapEntryList? RBRACE                         #mapLiteral
    | primaryExpression DOT identifier                                #dereference
    | primaryExpression LBRACKET expression RBRACKET                  #subscriptExpression
    | identifier                                                     #columnReference
    | userVariable                                                   #variableReference
    | number                                                         #numberLiteral
    | string                                                         #stringLiteral
    | identifier string                                             #typedStringLiteral
    | DEFAULT                                                        #defaultLiteral
    | NULL                                                           #nullLiteral
    | TRUE                                                           #booleanTrue
    | FALSE                                                          #booleanFalse
    | INTERVAL expression identifier                                 #intervalLiteral
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

windowSpec
    : LPAREN (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
    ;

windowFrame
    : (ROWS | RANGE) frameExtent
    ;

frameExtent
    : frameBound
    | BETWEEN start=frameBound AND end=frameBound
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
    ;

functionFilterClause
    : FILTER LPAREN WHERE expression RPAREN
    ;

expressionList
    : expression (COMMA expression)*
    ;

mapEntryList
    : mapEntry (COMMA mapEntry)*
    ;

mapEntry
    : expression COLON expression
    ;

// ============ DML Statements ============

explainStatement
    : EXPLAIN (LOGICAL | VERBOSE | COSTS | ANALYZE)? (query | insertStatement)
    ;

submitTaskStatement
    : SUBMIT TASK identifier? taskScheduleClause? propertiesClause? AS (insertStatement | createTableStatement | cacheSelectStatement | query)
    ;

taskScheduleClause
    : SCHEDULE (START LPAREN expression RPAREN)? EVERY LPAREN intervalLiteralValue RPAREN
    ;

cacheSelectStatement
    : CACHE query
    ;

alterTaskStatement
    : ALTER TASK (IF EXISTS)? identifier taskAction
    ;

taskAction
    : RESUME
    | SUSPEND
    | SET LPAREN propertyList RPAREN
    ;

dropTaskStatement
    : DROP TASK (IF EXISTS)? identifier FORCE?
    ;

insertStatement
    : ctes? INSERT (INTO | OVERWRITE) TABLE? (targetTable=multipartIdentifier | filesTarget)
      tableVersionClause?
      insertLabelClause?
      partitionClause?
      insertLabelClause?
      insertColumnMapping?
      propertiesClause?
      (query | VALUES valuesClause (COMMA valuesClause)*)
    ;

filesTarget
    : FILES LPAREN propertyList RPAREN
    ;

insertColumnMapping
    : LPAREN columnList=identifierList RPAREN
    | BY NAME
    ;

insertLabelClause
    : WITH LABEL identifier
    ;

exportTableStatement
    : EXPORT TABLE source=multipartIdentifier partitionClause? (LPAREN identifierList RPAREN)?
      TO string propertiesClause? (WITH BROKER string? (LPAREN propertyList RPAREN)?)?
    ;

cancelExportStatement
    : CANCEL EXPORT (FROM identifier)? WHERE expression
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

createRoutineLoadStatement
    : CREATE ROUTINE LOAD job=multipartIdentifier ON target=multipartIdentifier (routineLoadClause COMMA?)*
    ;

alterRoutineLoadStatement
    : ALTER ROUTINE LOAD FOR? job=multipartIdentifier (routineLoadClause COMMA?)*
    ;

routineLoadClause
    : COLUMNS TERMINATED BY string
    | ROWS TERMINATED BY string
    | COLUMNS LPAREN loadColumnList RPAREN
    | whereClause
    | PARTITION LPAREN identifierList RPAREN
    | TEMPORARY PARTITION LPAREN identifierList RPAREN
    | propertiesClause
    | FROM identifier LPAREN propertyList RPAREN
    ;

routineLoadControlStatement
    : (STOP | PAUSE | RESUME) ROUTINE LOAD FOR? job=multipartIdentifier
    ;

cancelLoadStatement
    : CANCEL LOAD (FROM identifier)? WHERE expression
    ;

createPipeStatement
    : CREATE (OR REPLACE)? PIPE (IF NOT EXISTS)? multipartIdentifier propertiesClause? AS insertStatement
    ;

alterPipeStatement
    : ALTER PIPE multipartIdentifier pipeAction
    ;

pipeAction
    : SET PROPERTY? LPAREN propertyList RPAREN
    | SUSPEND
    | RESUME (IF SUSPENDED)?
    | RETRY (ALL | FILE string)
    ;

dropPipeStatement
    : DROP PIPE (IF EXISTS)? multipartIdentifier
    ;

createAnalyzeStatement
    : CREATE ANALYZE (FULL | SAMPLE)?
      (ALL | DATABASE identifier | (IF NOT EXISTS)? TABLE multipartIdentifier (LPAREN identifierList RPAREN)?)
      propertiesClause?
    ;

dropAnalyzeStatement
    : DROP ANALYZE number
    ;

dropStatsStatement
    : DROP (MULTIPLE COLUMNS)? STATS multipartIdentifier (LPAREN identifierList RPAREN)?
    ;

killAnalyzeStatement
    : KILL ANALYZE number
    ;

killStatement
    : KILL (CONNECTION | QUERY)? (number | string | identifier)
    ;

catalogStatement
    : CREATE EXTERNAL CATALOG (IF NOT EXISTS)? identifier (COMMENT string)? propertiesClause?
    | DROP CATALOG (IF EXISTS)? identifier
    ;

backupStatement
    : BACKUP (ALL EXTERNAL CATALOGS | EXTERNAL (CATALOG | CATALOGS) LPAREN identifierList RPAREN)?
      (DATABASE database=identifier)? SNAPSHOT snapshot=multipartIdentifier TO repository=identifier backupOnClause? propertiesClause?
    ;

recoverStatement
    : RECOVER DATABASE identifier
    | RECOVER TABLE multipartIdentifier
    | RECOVER PARTITION identifier FROM multipartIdentifier
    ;

restoreStatement
    : RESTORE restoreLegacyDatabaseClause? SNAPSHOT snapshot=multipartIdentifier FROM repository=identifier
      restoreExternalCatalogClause? restoreDatabaseClause? backupOnClause? propertiesClause?
    ;

restoreLegacyDatabaseClause
    : DATABASE identifier
    ;

restoreDatabaseClause
    : DATABASE source=identifier (AS target=identifier)?
    ;

restoreExternalCatalogClause
    : ALL EXTERNAL CATALOGS
    | EXTERNAL (CATALOG | CATALOGS) restoreExternalCatalogObject
      (COMMA EXTERNAL (CATALOG | CATALOGS) restoreExternalCatalogObject)*
    ;

restoreExternalCatalogObject
    : identifier backupObjectAlias?
    ;

backupOnClause
    : ON LPAREN backupObject (COMMA backupObject)* RPAREN
    ;

backupObject
    : ALL TABLES
    | (TABLE | TABLES)? multipartIdentifier partitionClause? backupObjectAlias?
    | ALL MATERIALIZED VIEWS
    | MATERIALIZED (VIEW | VIEWS) multipartIdentifier backupObjectAlias?
    | ALL VIEWS
    | (VIEW | VIEWS) multipartIdentifier backupObjectAlias?
    | ALL FUNCTIONS
    | (FUNCTION | FUNCTIONS) multipartIdentifier backupObjectAlias?
    ;

backupObjectAlias
    : AS identifier
    ;

repositoryStatement
    : CREATE READ? ONLY? REPOSITORY identifier WITH BROKER ON LOCATION string propertiesClause
    | DROP REPOSITORY identifier
    | CANCEL (BACKUP | RESTORE) ((FROM identifier) | (FOR EXTERNAL CATALOG))
    ;

storageVolumeStatement
    : CREATE STORAGE VOLUME (IF NOT EXISTS)? identifier storageVolumeOption*
    | ALTER STORAGE VOLUME identifier storageVolumeAction
    | DROP STORAGE VOLUME (IF EXISTS)? identifier
    ;

storageVolumeAction
    : ENABLE
    | DISABLE
    | SET storageVolumeOption+
    ;

storageVolumeOption
    : TYPE EQ? identifier
    | LOCATIONS EQ? LPAREN string (COMMA string)* RPAREN
    | COMMENT EQ? string
    | propertiesClause
    | identifier EQ? (identifier | string | number)
    ;

warehouseStatement
    : CREATE WAREHOUSE (IF NOT EXISTS)? identifier propertiesClause?
    | ALTER WAREHOUSE identifier warehouseAction
    | DROP WAREHOUSE (IF EXISTS)? identifier
    | SUSPEND WAREHOUSE identifier
    | RESUME WAREHOUSE identifier
    ;

warehouseAction
    : SET propertiesClause
    | SUSPEND
    | RESUME
    | RENAME TO identifier
    ;

resourceStatement
    : CREATE EXTERNAL? RESOURCE (IF NOT EXISTS)? resourceIdentifier propertiesClause?
    | ALTER RESOURCE resourceIdentifier SET propertiesClause
    | DROP RESOURCE (IF EXISTS)? resourceIdentifier
    ;

resourceIdentifier
    : identifier
    | string
    ;

resourceGroupStatement
    : CREATE RESOURCE GROUP identifier TO resourceClassifier (COMMA resourceClassifier)* WITH LPAREN propertyList RPAREN
    | ALTER RESOURCE GROUP identifier (ADD resourceClassifier (COMMA resourceClassifier)* | DROP (LPAREN numberList RPAREN | ALL) | WITH LPAREN propertyList RPAREN)
    | DROP RESOURCE GROUP (IF EXISTS)? identifier
    ;

resourceClassifier
    : LPAREN resourceClassifierCondition (COMMA resourceClassifierCondition)* RPAREN
    ;

resourceClassifierCondition
    : identifier (EQ string | IN LPAREN stringList RPAREN)
    ;

fileStatement
    : CREATE FILE string (IN identifier)? propertiesClause?
    | DROP FILE string (FROM identifier)? propertiesClause?
    ;

createFunctionStatement
    : CREATE (OR REPLACE)? GLOBAL? (AGGREGATE | TABLE)? FUNCTION multipartIdentifier LPAREN (functionParameterList | dataTypeList)? RPAREN
      RETURNS functionReturnTail
    ;

functionReturnTail
    : ~SEMI+
    ;

dropFunctionStatement
    : DROP GLOBAL? FUNCTION (IF EXISTS)? multipartIdentifier (LPAREN dataTypeList? RPAREN)?
    ;

functionParameterList
    : functionParameter (COMMA functionParameter)*
    ;

functionParameter
    : identifier dataType
    ;

createDictionaryStatement
    : CREATE DICTIONARY name=multipartIdentifier USING source=multipartIdentifier
      LPAREN dictionaryColumn (COMMA dictionaryColumn)* RPAREN
      propertiesClause?
    ;

dictionaryColumn
    : identifier (KEY | VALUE)
    ;

refreshDictionaryStatement
    : REFRESH DICTIONARY multipartIdentifier
    ;

cancelRefreshDictionaryStatement
    : CANCEL REFRESH DICTIONARY multipartIdentifier
    ;

dropDictionaryStatement
    : DROP DICTIONARY multipartIdentifier CACHE?
    ;

adminStatement
    : ADMIN SET FRONTEND CONFIG LPAREN propertyList RPAREN
    | ADMIN SET REPLICA STATUS propertiesClause
    | ADMIN .+?
    ;

adminShowStatement
    : ADMIN SHOW REPLICA (STATUS | DISTRIBUTION) FROM multipartIdentifier partitionClause? whereClause?
    | ADMIN SHOW TABLET STATUS FROM multipartIdentifier partitionClause? whereClause? propertiesClause?
    ;

adminRepairStatement
    : ADMIN CANCEL? REPAIR TABLE multipartIdentifier partitionClause? propertiesClause?
    ;

adminCheckTabletStatement
    : ADMIN CHECK TABLET LPAREN numberList RPAREN propertiesClause
    ;

adminSetPartitionVersionStatement
    : ADMIN SET TABLE multipartIdentifier PARTITION LPAREN (identifier | number) RPAREN VERSION TO number
    ;

loadLabelStatement
    : LOAD LABEL label=multipartIdentifier LPAREN loadDataElement (COMMA loadDataElement)* RPAREN
      ((WITH BROKER brokerName? (LPAREN propertyList RPAREN)?)
      | (WITH RESOURCE resourceName (LPAREN propertyList RPAREN)?))?
      propertiesClause?
    ;

brokerName
    : identifier
    | string
    ;

resourceName
    : identifier
    | string
    ;

alterLoadStatement
    : ALTER LOAD FOR label=multipartIdentifier propertiesClause
    ;

alterSystemStatement
    : ALTER SYSTEM CREATE IMAGE
    | ALTER SYSTEM DROP ALL BROKER identifier
    | ALTER SYSTEM (ADD | DROP | DECOMMISSION) systemNodeTarget
    ;

systemNodeTarget
    : (FOLLOWER | OBSERVER | BACKEND) stringList
    | COMPUTE NODE stringList
    | BROKER identifier? stringList
    ;

cancelDecommissionStatement
    : CANCEL DECOMMISSION BACKEND stringList
    ;

backendBlacklistStatement
    : (ADD | DELETE) (BACKEND | COMPUTE NODE) BLACKLIST numberList
    ;

sqlBlacklistStatement
    : ADD SQLBLACKLIST string
    | DELETE SQLBLACKLIST numberList
    ;

syncStatement
    : SYNC
    ;

loadDataElement
    : DATA INFILE LPAREN string (COMMA string)* RPAREN NEGATIVE? INTO TABLE target=multipartIdentifier loadDataOption*
    | DATA FROM TABLE source=multipartIdentifier NEGATIVE? INTO TABLE target=multipartIdentifier loadDataOption*
    ;

loadDataOption
    : COLUMNS TERMINATED BY string
    | ROWS TERMINATED BY string
    | FORMAT AS string
    | LPAREN propertyList RPAREN
    | LPAREN loadColumnList RPAREN
    | COLUMNS FROM PATH AS LPAREN identifierList RPAREN
    | whereClause
    | SET LPAREN assignmentList RPAREN
    | SET assignmentList
    | PARTITION LPAREN identifierList RPAREN
    | TEMPORARY PARTITION LPAREN identifierList RPAREN
    ;

loadColumnList
    : loadColumnItem (COMMA loadColumnItem)*
    ;

loadColumnItem
    : identifier
    | assignment
    ;

updateStatement
    : ctes? UPDATE multipartIdentifier tableAlias
      SET assignmentList
      (FROM relationList)?
      whereClause?
    ;

deleteStatement
    : ctes? DELETE FROM multipartIdentifier partitionClause? tableAlias
      (USING relationList)?
      whereClause?
    ;

mergeStatement
    : ctes? MERGE INTO multipartIdentifier tableAlias
      USING (multipartIdentifier tableAlias | LPAREN query RPAREN tableAlias)
      ON expression
      mergeClause+
    ;

mergeClause
    : WHEN MATCHED mergeCondition? THEN mergeMatchedAction
    | WHEN NOT MATCHED mergeCondition? THEN mergeNotMatchedAction
    ;

mergeCondition
    : AND expression
    ;

mergeMatchedAction
    : UPDATE SET assignmentList
    | DELETE
    ;

mergeNotMatchedAction
    : INSERT (LPAREN identifierList RPAREN)? VALUES LPAREN expressionList RPAREN
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : multipartIdentifier EQ expression
    ;

// ============ DDL Statements ============

createTableStatement
    : CREATE TEMPORARY? EXTERNAL? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN (tableElementList | ctasElements=ctasElementList) RPAREN)?
      engineClause?
      keyDesc?
      commentClause?
      partitionDesc?
      distributionDesc?
      rollupClause?
      orderByDesc?
      propertiesClause?
      (AS query)?
    | CREATE TEMPORARY? EXTERNAL? TABLE (IF NOT EXISTS)? target=multipartIdentifier
      partitionDesc?
      distributionDesc?
      propertiesClause?
      LIKE source=multipartIdentifier
    ;

engineClause
    : ENGINE EQ? identifier
    ;

createViewStatement
    : CREATE MATERIALIZED? (OR REPLACE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=viewColumnDefinitionList RPAREN)?
      materializedViewOption*
      (SECURITY (NONE | INVOKER))?
      AS query
    ;

alterViewStatement
    : ALTER VIEW multipartIdentifier
      (LPAREN alterViewColumnList RPAREN)?
      AS query
    ;

alterViewColumnList
    : alterViewColumn (COMMA alterViewColumn)*
    ;

alterViewColumn
    : identifier commentClause?
    ;

viewColumnDefinitionList
    : viewColumnDefinition (COMMA viewColumnDefinition)*
    ;

viewColumnDefinition
    : identifier commentClause?
    ;

materializedViewOption
    : commentClause
    | BUILD (IMMEDIATE | DEFERRED)
    | partitionDesc
    | distributionDesc
    | orderByDesc
    | REFRESH (IMMEDIATE | DEFERRED)? (SYNC | ASYNC | MANUAL)? (SCHEDULE? refreshSchedule)?
    | propertiesClause
    ;

refreshSchedule
    : START LPAREN expression RPAREN EVERY LPAREN intervalLiteralValue RPAREN
    | EVERY LPAREN intervalLiteralValue RPAREN
    ;

createIndexStatement
    : CREATE INDEX identifier ON multipartIdentifier LPAREN identifierList RPAREN (USING identifier)? indexPropertyClause? commentClause?
    ;

createDatabaseStatement
    : CREATE DATABASE (IF NOT EXISTS)? identifier propertiesClause?
    ;

dropIndexStatement
    : DROP INDEX identifier ON multipartIdentifier
    ;

dropDatabaseStatement
    : DROP DATABASE (IF EXISTS)? identifier FORCE?
    ;

alterDatabaseStatement
    : ALTER DATABASE identifier alterDatabaseAction
    ;

alterDatabaseAction
    : RENAME identifier
    | SET DATA QUOTA number identifier?
    | SET REPLICA QUOTA number
    | SET (propertiesClause | LPAREN propertyList RPAREN)
    ;

dropTableStatement
    : DROP TEMPORARY? TABLE (IF EXISTS)? multipartIdentifier FORCE?
    ;

dropViewStatement
    : DROP MATERIALIZED? VIEW (IF EXISTS)? multipartIdentifier FORCE?
    ;

truncateTableStatement
    : TRUNCATE TABLE multipartIdentifier partitionClause?
    ;

refreshExternalTableStatement
    : REFRESH EXTERNAL TABLE multipartIdentifier partitionClause?
    ;

refreshMaterializedViewStatement
    : REFRESH MATERIALIZED VIEW multipartIdentifier refreshMaterializedViewOption*
    ;

refreshMaterializedViewOption
    : PARTITION identifier
    | PARTITION START LPAREN expression RPAREN END LPAREN expression RPAREN
    | FORCE
    | WITH (SYNC | ASYNC) MODE
    ;

cancelRefreshMaterializedViewStatement
    : CANCEL REFRESH MATERIALIZED VIEW multipartIdentifier FORCE?
    ;

alterMaterializedViewStatement
    : ALTER MATERIALIZED VIEW multipartIdentifier alterMaterializedViewAction?
    ;

alterMaterializedViewAction
    : RENAME (TO | AS)? multipartIdentifier
    | SWAP WITH multipartIdentifier
    | SET (propertiesClause | LPAREN propertyList RPAREN)
    | (ACTIVE | INACTIVE)
    | REFRESH (SYNC | ASYNC | MANUAL)? (SCHEDULE? refreshSchedule)?
    | identifier .+?
    | .+?
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME (TO | AS)? multipartIdentifier     #alterTableRename
    | ALTER TABLE multipartIdentifier SWAP WITH TABLE multipartIdentifier        #alterTableSwap
    | ALTER TABLE multipartIdentifier alterTableAction (COMMA alterTableAction)* #alterTableOther
    ;

alterTableAction
    : ADD COLUMN? identifier dataType generatedColumn? aggregateType? columnConstraint* columnPosition? rollupTarget? propertiesClause?
    | DROP COLUMN (IF EXISTS)? identifier rollupSource?
    | ADD COLUMNS? LPAREN tableElementList RPAREN rollupTarget? propertiesClause?
    | ADD COLUMN? LPAREN tableElementList RPAREN rollupTarget? propertiesClause?
    | MODIFY COLUMN? identifier dataType? generatedColumn? aggregateType? columnConstraint* columnPosition? rollupSource? propertiesClause?
    | MODIFY COLUMN? identifier ADD FIELD fieldPath dataType
    | MODIFY COLUMN? identifier DROP FIELD fieldPath
    | RENAME COLUMN identifier TO identifier
    | RENAME ROLLUP identifier identifier
    | RENAME PARTITION identifier identifier
    | ORDER BY (LPAREN identifierList RPAREN | identifierList) rollupSource? propertiesClause?
    | DISTRIBUTED BY HASH LPAREN identifierList RPAREN (DEFAULT? BUCKETS number)?
    | DISTRIBUTED BY RANDOM (BUCKETS number)?
    | PARTITIONS? (LPAREN identifierList RPAREN | identifierList)? DISTRIBUTED BY HASH LPAREN identifierList RPAREN (BUCKETS number)?
    | PARTITIONS? (LPAREN identifierList RPAREN | identifierList)? DISTRIBUTED BY RANDOM (BUCKETS number)?
    | ADD ROLLUP rollupDefinition (COMMA rollupDefinition)*
    | DROP ROLLUP rollupDropDefinition (COMMA rollupDropDefinition)*
    | ADD INDEX identifier LPAREN identifierList RPAREN (USING identifier)? indexPropertyClause? commentClause?
    | DROP INDEX identifier
    | ADD TEMPORARY? PARTITION (IF NOT EXISTS)? identifier partitionValueDesc distributionDesc? partitionProperties?
    | ADD TEMPORARY? PARTITIONS START LPAREN expression RPAREN END LPAREN expression RPAREN EVERY everyValue distributionDesc? partitionProperties?
    | CREATE (OR REPLACE)? TAG (IF NOT EXISTS)? identifier (AS OF VERSION expression)? (RETAIN number retentionUnit)?
    | DROP (BRANCH | TAG) identifier
    | DROP PARTITION (IF EXISTS)? identifier FORCE?
    | DROP PARTITIONS (IF EXISTS)? LPAREN identifierList RPAREN FORCE?
    | DROP PARTITIONS (IF EXISTS)? START LPAREN expression RPAREN END LPAREN expression RPAREN EVERY everyValue FORCE?
    | DROP PARTITIONS WHERE expression
    | DROP TEMPORARY PARTITION identifier
    | REPLACE PARTITION identifier WITH TEMPORARY PARTITION identifier
    | MODIFY TEMPORARY? PARTITION identifier SET LPAREN propertyList RPAREN
    | MODIFY TEMPORARY? PARTITION LPAREN STAR RPAREN SET LPAREN propertyList RPAREN
    | MODIFY TEMPORARY? PARTITIONS? LPAREN identifierList RPAREN SET LPAREN propertyList RPAREN
    | RECOVER PARTITION identifier
    | (CUMULATIVE | BASE)? COMPACT (LPAREN identifierList RPAREN | identifier)?
    | SPLIT (TABLET | TABLETS) tabletAlterTarget? propertiesClause?
    | MERGE (TABLET | TABLETS) tabletAlterTarget? propertiesClause?
    | DROP PERSISTENT INDEX ON TABLETS LPAREN identifierList RPAREN
    | SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

analyzeTableStatement
    : ANALYZE (FULL | SAMPLE)? TABLE multipartIdentifier
      (LPAREN identifierList RPAREN)?
      analyzeHistogramClause?
      analyzeMode?
      analyzeBuckets?
      propertiesClause?
    ;

analyzeHistogramClause
    : (UPDATE | DROP) HISTOGRAM ON identifierList
    ;

analyzeMode
    : WITH (SYNC | ASYNC) MODE
    ;

analyzeBuckets
    : WITH number BUCKETS
    ;

cancelAlterTableStatement
    : CANCEL ALTER TABLE (COLUMN | OPTIMIZE | ROLLUP) FROM multipartIdentifier (LPAREN numberList RPAREN)?
    ;

tabletAlterTarget
    : (PARTITION | PARTITIONS) LPAREN identifierList RPAREN (LPAREN numberList RPAREN)?
    | LPAREN numberList RPAREN
    ;

showStatement
    : SHOW CREATE TABLE multipartIdentifier
    | SHOW CREATE DATABASE identifier
    | SHOW CREATE MATERIALIZED? VIEW multipartIdentifier
    | SHOW CREATE CATALOG identifier
    | SHOW CATALOGS (LIKE string)? queryOrganization
    | SHOW DATABASES (FROM identifier)?
    | SHOW DATA (FROM multipartIdentifier)?
    | SHOW FILE (FROM identifier)?
    | SHOW DELETE (FROM identifier)?
    | SHOW DYNAMIC PARTITION TABLES FROM identifier
    | SHOW ALTER MATERIALIZED VIEW ((FROM | IN) identifier)?
    | SHOW ALTER TABLE (COLUMN | OPTIMIZE | ROLLUP) (FROM identifier)? whereClause? queryOrganization
    | SHOW MATERIALIZED (VIEW | VIEWS) (FROM identifier)? (whereClause | LIKE string)?
    | SHOW DICTIONARY multipartIdentifier?
    | SHOW FULL? COLUMNS FROM multipartIdentifier (FROM identifier)?
    | SHOW (INDEX | INDEXES | KEY | KEYS) FROM multipartIdentifier (FROM identifier)?
    | SHOW FULL? (TABLES | VIEWS) ((FROM | IN) qualifiedName)? (LIKE string | whereClause)?
    | SHOW GRANTS
    | SHOW GRANTS .+?
    | SHOW ROLES
    | SHOW USERS
    | SHOW ALL? AUTHENTICATION (FOR (identifier | string))?
    | SHOW PROPERTY (FOR (identifier | string))? (LIKE string)?
    | SHOW (BACKUP | RESTORE) (FROM identifier)?
    | SHOW REPOSITORIES
    | SHOW SNAPSHOT ON identifier whereClause?
    | SHOW LOAD (FROM identifier)? whereClause? queryOrganization
    | SHOW ALL? ROUTINE LOAD ((FOR qualifiedName) | (FROM identifier))? whereClause? queryOrganization
    | SHOW ROUTINE LOAD TASK (FROM identifier)? whereClause?
    | SHOW EXPORT (FROM identifier)? whereClause? queryOrganization
    | SHOW TRANSACTION (FROM identifier)? WHERE expression
    | SHOW ANALYZE (JOB | STATUS) whereClause? queryOrganization
    | SHOW (STATS | HISTOGRAM) META whereClause?
    | SHOW FULL? BUILTIN? FUNCTIONS ((FROM | IN) identifier)? (LIKE string)?
    | SHOW (GLOBAL | SESSION)? VARIABLES ((LIKE string) | whereClause)?
    | SHOW TABLE STATUS (FROM identifier)? (LIKE string)?
    | SHOW TABLET FROM multipartIdentifier partitionClause? whereClause? queryOrganization
    | SHOW TABLET number
    | SHOW PROC string
    | SHOW FULL? PROCESSLIST
    | SHOW (BACKENDS | FRONTENDS | BROKER | RUNNING QUERIES | COMPUTE NODES)
    | SHOW (BACKEND | COMPUTE NODE) BLACKLIST
    | SHOW SQLBLACKLIST
    | SHOW RESOURCE GROUPS ALL?
    | SHOW RESOURCE GROUP identifier
    | SHOW USAGE RESOURCE GROUPS
    | SHOW STORAGE VOLUMES (LIKE string)?
    | SHOW WAREHOUSES (LIKE string | whereClause)? queryOrganization
    | SHOW RESOURCES whereClause? queryOrganization
    | SHOW PIPES (FROM identifier)? whereClause? queryOrganization
    | SHOW TEMPORARY? PARTITIONS FROM multipartIdentifier whereClause? queryOrganization
    | SHOW showObject (FROM | IN) multipartIdentifier
    | SHOW .+?
    ;

showObject
    : identifier
    | KEY
    ;

describeStatement
    : (DESCRIBE | DESC) (TABLE? multipartIdentifier ALL? | FILES LPAREN propertyList RPAREN)
    ;

transactionControlStatement
    : BEGIN
    | START TRANSACTION
    | COMMIT
    | ROLLBACK
    ;

preparedStatement
    : PREPARE identifier FROM string
    | EXECUTE identifier (USING userVariable (COMMA userVariable)*)?
    | (DROP | DEALLOCATE) PREPARE identifier
    ;

accountControlStatement
    : CREATE USER .+?
    | ALTER USER .+?
    | DROP USER (IF EXISTS)? .+?
    | CREATE ROLE .+?
    | DROP ROLE .+?
    | SET ROLE .+?
    | SET DEFAULT ROLE .+? TO .+?
    | SET PASSWORD .+?
    | EXECUTE AS .+? WITH NO REVERT
    | GRANT .+? ON TABLE multipartIdentifier .+?
    | REVOKE .+? ON TABLE multipartIdentifier .+?
    | GRANT .+? ON VIEW multipartIdentifier .+?
    | REVOKE .+? ON VIEW multipartIdentifier .+?
    | GRANT .+? ON MATERIALIZED VIEW multipartIdentifier .+?
    | REVOKE .+? ON MATERIALIZED VIEW multipartIdentifier .+?
    | GRANT .+? ON multipartIdentifier .+?
    | REVOKE .+? ON multipartIdentifier .+?
    | GRANT .+?
    | REVOKE .+?
    ;

useStatement
    : USE identifier
    ;

setCatalogStatement
    : SET CATALOG identifier
    ;

setStatement
    : SET (GLOBAL | identifier)? setAssignment (COMMA setAssignment)*
    ;

setAssignment
    : (identifier | userVariable) EQ expression
    ;

commentStatement
    : COMMENT ON (TABLE multipartIdentifier | COLUMN multipartIdentifier)
      IS string
    ;

// ============ DDL Helpers ============

tableElementList
    : tableElement (COMMA tableElement)*
    ;

tableElement
    : identifier dataType generatedColumn? aggregateType? columnConstraint*
    | indexDefinition
    ;

ctasElementList
    : ctasElement (COMMA ctasElement)*
    ;

ctasElement
    : identifier
    | indexDefinition
    ;

indexDefinition
    : INDEX identifier LPAREN identifierList RPAREN (USING identifier)? indexPropertyClause? commentClause?
    ;

generatedColumn
    : AS expression
    ;

aggregateType
    : SUM
    | MIN
    | MAX
    | REPLACE
    | HLL_UNION
    | BITMAP_UNION
    | REPLACE_IF_NOT_NULL
    ;

columnConstraint
    : NOT NULL
    | NULL
    | COMMENT string
    | DEFAULT expression
    | AUTO_INCREMENT
    ;

columnPosition
    : FIRST
    | AFTER identifier
    ;

rollupDefinition
    : identifier LPAREN identifierList RPAREN (FROM identifier)? propertiesClause?
    ;

rollupClause
    : ROLLUP LPAREN rollupDefinition (COMMA rollupDefinition)* RPAREN
    ;

rollupDropDefinition
    : identifier propertiesClause?
    ;

rollupTarget
    : TO identifier
    ;

rollupSource
    : FROM identifier
    ;

fieldPath
    : fieldPathPart (DOT fieldPathPart)*
    ;

fieldPathPart
    : identifier
    | LBRACKET STAR RBRACKET
    ;

keyDesc
    : (DUPLICATE | AGGREGATE | UNIQUE | PRIMARY) KEY LPAREN identifierList RPAREN
    ;

commentClause
    : COMMENT string
    ;

indexPropertyClause
    : LPAREN propertyList RPAREN
    ;

distributionDesc
    : DISTRIBUTED BY HASH LPAREN identifierList RPAREN (DEFAULT? BUCKETS number)?
    | DISTRIBUTED BY RANDOM (BUCKETS number)?
    ;

orderByDesc
    : ORDER BY LPAREN identifierList RPAREN
    ;

propertiesClause
    : PROPERTIES LPAREN propertyList RPAREN
    ;

partitionDesc
    : PARTITION BY RANGE LPAREN identifierList RPAREN
      LPAREN (partitionDefinition (COMMA partitionDefinition)* | partitionBatchDefinition) RPAREN
    | PARTITION BY LPAREN expressionList RPAREN
    | PARTITION BY expression
    ;

intervalLiteralValue
    : INTERVAL expression identifier
    ;

partitionDefinition
    : PARTITION identifier partitionValueDesc
    ;

partitionBatchDefinition
    : START LPAREN expression RPAREN END LPAREN expression RPAREN EVERY everyValue
    ;

partitionValueDesc
    : VALUES LESS THAN LPAREN expressionList RPAREN
    | VALUES IN LPAREN partitionValueList RPAREN
    | VALUES (LBRACKET | LPAREN) partitionValueItem COMMA partitionValueItem (RBRACKET | RPAREN)
    ;

partitionValueList
    : partitionValueItem (COMMA partitionValueItem)*
    ;

partitionValueItem
    : expression
    | LPAREN expressionList RPAREN
    ;

partitionClause
    : TEMPORARY? PARTITION (LPAREN partitionItemList RPAREN | identifier)
    ;

tableVersionClause
    : FOR? (VERSION | TIMESTAMP) AS OF expression
    ;

tableHintClause
    : LBRACKET identifier RBRACKET
    ;

retentionUnit
    : DAYS
    | HOURS
    | MINUTES
    ;

partitionItemList
    : partitionItem (COMMA partitionItem)*
    ;

partitionItem
    : identifier (EQ expression)?
    ;

propertyList
    : property (COMMA property)*
    ;

property
    : (string | identifier) EQ (string | identifier | number | TRUE | FALSE)
    ;

stringList
    : string (COMMA string)*
    ;

partitionProperties
    : propertiesClause
    | LPAREN propertyList RPAREN
    ;

everyValue
    : intervalLiteralValue
    | number
    | LPAREN (intervalLiteralValue | number) RPAREN
    ;

dataType
    : STRUCT LT structField (COMMA structField)* GT
    | identifier (LPAREN NUMBER_LITERAL (COMMA NUMBER_LITERAL)* RPAREN)?
    | identifier LT dataType (COMMA dataType)* GT
    ;

structField
    : identifier dataType
    ;

dataTypeList
    : dataType (COMMA dataType)*
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

numberList
    : number (COMMA number)*
    ;

identifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | nonReservedKeyword
    ;

userVariable
    : USER_VARIABLE
    ;

strictIdentifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | nonReservedKeyword
    ;

nonReservedKeyword
    : ACTIVE | ADD | ADMIN | AFTER | AGGREGATE | ANALYZE | ASC | AUTHENTICATION | AUTO_INCREMENT | BACKEND | BACKUP | BASE | BEGIN | BLACKLIST | BUCKETS | BUILD | BUILTIN | CACHE | CANCEL | CAST | CHECK | COLUMN | COLUMNS | COMMENT | COMMIT | COMPUTE | CONFIG | CUMULATIVE | DATABASES | DEALLOCATE | DECOMMISSION | DEFAULT
    | ANTI | ANY | ASOF | BRANCH | BROKER | CONNECTION | COSTS | CURRENT | DATA | DATABASE | DAYS | DESCRIBE | DESC | DICTIONARY | DISTRIBUTED | DISTRIBUTION | DROP | DUPLICATE | DYNAMIC | END | ENGINE | ESCAPE | EXISTS | EXCLUDE | EXPLAIN | EXPORT | EXTERNAL | FALSE | FOLLOWER
    | DEFERRED | EXECUTE | FIELD | FILTER | FUNCTIONS | GRANT | GRANTS | GROUPS | IMMEDIATE | INACTIVE
    | ASYNC | BACKENDS | BITMAP_UNION | CUBE | EVERY | FIRST | FOLLOWING | FOR | FORCE | FORMAT | FRONTEND | FRONTENDS | FUNCTION | GLOBAL | GROUPING | HASH | HISTOGRAM | HLL_UNION | HOURS | IF | IMAGE | INDEX | INDEXES | INFILE | INTERVAL | JOB | KEY | KEYS | KILL | LABEL | LAST | LATERAL | LESS | LIKE | LIMIT | LOAD | LOGICAL | MATERIALIZED | MINUTES
    | INVOKER | MANUAL | MATCH | MATCH_ALL | MATCH_ANY | MATCHED | MAX | MERGE | META | MIN | MINUS_KW | MODE | MULTIPLE | NEGATIVE | NODE | NODES | NONE | NULL | NULLS | OBSERVER | OLAP
    | OF | OFFSET | ONLY | OPTIMIZE | OUTFILE | OVER | OVERWRITE | PARTITION | PARTITIONS | PAUSE | PREPARE | PRIMARY | PROPERTIES | QUALIFY | QUOTA
    | NAME | PATH | PRECEDING | PROC | PROCESSLIST | QUERIES | QUERY | RANDOM | RANGE | READ | RECURSIVE | REFRESH | REGEXP | RENAME | REPAIR | REPLACE | REPLACE_IF_NOT_NULL | REPLICA | REPOSITORIES | REPOSITORY | RESUME | RESTORE | RETURNS | REVOKE | RLIKE | ROLLBACK | ROLE | ROLES | ROLLUP | ROUTINE | ROW | ROWS | RUNNING | SAMPLE | SCHEDULE | SECURITY | SEMI_JOIN | SESSION | SETS | SHOW | SNAPSHOT | SOME | SQLBLACKLIST | START | STATUS | STATS | STOP | STORED | SUBMIT | SUM | SWAP | SYNC | SYSTEM | TABLE | TABLES | TASK | TRANSACTION | UNBOUNDED | USER | USERS | VARIABLES | VIEWS
    | CATALOG | CATALOGS | FILE | FILES | PIPE | PIPES | PIVOT | PROPERTY | RETRY | SUSPEND | SUSPENDED | VOLUMES | WAREHOUSE | WAREHOUSES
    | RETAIN | SPLIT | STRUCT | TABLET | TAG | TEMPORARY | TERMINATED | THAN | TIMESTAMP | TO | TRUE | TRUNCATE | UNIQUE | USAGE | VALUE | VALUES | VERSION | VERBOSE | VIEW
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
