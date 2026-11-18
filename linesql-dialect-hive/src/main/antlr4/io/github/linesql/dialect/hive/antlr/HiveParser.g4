parser grammar HiveParser;

options { tokenVocab = HiveLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | multiInsertStatement                                           #multiInsertStmt
    | explainStatement                                               #explainStmt
    | insertDirectoryStatement                                       #insertDirectoryStmt
    | insertStatement                                                #insertStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | mergeStatement                                                 #mergeStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | createMaterializedViewStatement                                #createMaterializedViewStmt
    | dropViewStatement                                              #dropViewStmt
    | alterViewStatement                                             #alterViewStmt
    | dropTableStatement                                             #dropTableStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
    | createDatabaseStatement                                        #createDatabaseStmt
    | alterDatabaseStatement                                         #alterDatabaseStmt
    | dropDatabaseStatement                                          #dropDatabaseStmt
    | connectorStatement                                             #connectorStmt
    | scheduledQueryStatement                                        #scheduledQueryStmt
    | useStatement                                                   #useStmt
    | setStatement                                                   #setStmt
    | resetStatement                                                 #resetStmt
    | dfsStatement                                                   #dfsStmt
    | resourceStatement                                              #resourceStmt
    | functionStatement                                              #functionStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | commentStatement                                               #commentStmt
    | loadDataStatement                                              #loadDataStmt
    | exportTableStatement                                           #exportTableStmt
    | importTableStatement                                           #importTableStmt
    | repairTableStatement                                           #repairTableStmt
    | analyzeTableStatement                                          #analyzeTableStmt
    | lockTableStatement                                             #lockTableStmt
    | grantStatement                                                 #grantStmt
    | revokeStatement                                                #revokeStmt
    | roleStatement                                                  #roleStmt
    ;

multiInsertStatement
    : ctes? fromClause multiInsertBody+
    ;

multiInsertBody
    : INSERT (INTO | OVERWRITE) TABLE? multipartIdentifier
      (PARTITION partitionSpec)?
      (IF NOT EXISTS)?
      (LPAREN columnList=identifierList RPAREN)?
      selectClause whereClause? groupByClause? havingClause? queryOrganization
    ;

explainStatement
    : EXPLAIN explainOption* explainedStatement
    ;

explainOption
    : EXTENDED
    | FORMATTED
    | DEPENDENCY
    | AUTHORIZATION
    ;

explainedStatement
    : query
    | insertDirectoryStatement
    | insertStatement
    | updateStatement
    | deleteStatement
    | mergeStatement
    | createTableStatement
    | createViewStatement
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
    | LPAREN query RPAREN                                            #subqueryPrimary
    ;

querySpecification
    : selectClause fromClause? whereClause? groupByClause? havingClause? windowClause? qualifyClause?
    ;

selectClause
    : SELECT setQuantifier? (selectItemList | transformClause)
    ;

transformClause
    : (TRANSFORM | MAP | REDUCE) LPAREN expressionList? RPAREN USING string AS identifierList
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
    : relationPrimary lateralView* joinRelation*
    ;

lateralView
    : LATERAL VIEW OUTER? functionName LPAREN expressionList RPAREN identifier AS identifier (COMMA identifier)*
    | COMMA? LATERAL TABLE LPAREN inlineValues RPAREN tableAlias (LPAREN identifierList RPAREN)?
    ;

inlineValues
    : VALUES valuesClause (COMMA valuesClause)*
    ;

relationPrimary
    : multipartIdentifier tableSampleClause? tableTimeTravel? tableAlias #tableName
    | TABLE LPAREN inlineValues RPAREN tableAlias (LPAREN identifierList RPAREN)? #inlineValuesTable
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    ;

tableTimeTravel
    : FOR (SYSTEM_TIME | SYSTEM_VERSION) AS OF expression
    ;

tableSampleClause
    : TABLESAMPLE LPAREN BUCKET bucketNumber=expression OUT OF bucketCount=expression
      (ON sampleBy=expression)? RPAREN
    | TABLESAMPLE LPAREN expression PERCENT_KW RPAREN
    | TABLESAMPLE LPAREN expression ROWS RPAREN
    | TABLESAMPLE LPAREN number identifier RPAREN
    ;

joinRelation
    : joinType? JOIN relationPrimary joinCriteria?
    ;

joinType
    : INNER
    | LEFT SEMI_KW
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

groupByClause
    : GROUP BY groupByItem (COMMA groupByItem)* legacyGroupByModifier?
    ;

legacyGroupByModifier
    : WITH (ROLLUP | CUBE)
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
    : (ORDER BY sortItem (COMMA sortItem)*)?
      (DISTRIBUTE BY distributeByExpressions+=expression (COMMA distributeByExpressions+=expression)*)?
      (SORT BY sortByItems+=sortItem (COMMA sortByItems+=sortItem)*)?
      (CLUSTER BY clusterByExpressions+=expression (COMMA clusterByExpressions+=expression)*)?
      (LIMIT expression (COMMA expression)?)?
      (OFFSET expression)?
    ;

sortItem
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

// ============ Expressions ============

expression
    : booleanExpression
    ;

booleanExpression
    : (NOT | BANG) booleanExpression                                 #logicalNot
    | valueExpression predicate?                                     #predicatedExpr
    | left=booleanExpression AND right=booleanExpression              #logicalAnd
    | left=booleanExpression OR right=booleanExpression               #logicalOr
    | EXISTS LPAREN query RPAREN                                     #existsExpr
    ;

predicate
    : NOT? BETWEEN lower=valueExpression AND upper=valueExpression
    | NOT? IN LPAREN (expressionList | query) RPAREN
    | NOT? LIKE valueExpression
    | NOT? (RLIKE | REGEXP) valueExpression
    | IS NOT? (NULL | TRUE | FALSE)
    ;

valueExpression
    : primaryExpression                                              #valueExpressionDefault
    | operator=(MINUS | PLUS | TILDE) valueExpression                #unaryExpression
    | left=valueExpression operator=(STAR | SLASH | PERCENT | DIV) right=valueExpression   #arithmeticBinary
    | left=valueExpression operator=(PLUS | MINUS) right=valueExpression             #arithmeticBinaryPlusMinus
    | left=valueExpression operator=(AMP | PIPE | CARET) right=valueExpression        #bitwiseBinary
    | left=valueExpression CONCAT right=valueExpression              #concatExpression
    | left=valueExpression comparisonOperator right=valueExpression  #comparison
    ;

comparisonOperator
    : EQ | NULL_SAFE_EQ | NEQ | LT | GT | LTE | GTE
    ;

primaryExpression
    : CASE whenClause+ (ELSE elseExpr=expression)? END              #searchedCase
    | CASE operand=expression whenClause+ (ELSE elseExpr=expression)? END  #simpleCase
    | CAST LPAREN expression AS dataType RPAREN                      #castExpr
    | functionName LPAREN STAR RPAREN (OVER windowReference)?        #functionCallStar
    | functionName LPAREN setQuantifier? expressionList RPAREN (OVER windowReference)?  #functionCall
    | functionName LPAREN RPAREN (OVER windowReference)?             #functionCallEmpty
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | primaryExpression LBRACKET expression RBRACKET                 #subscriptExpression
    | primaryExpression DOT identifier                                #dereference
    | identifier                                                     #columnReference
    | number                                                         #numberLiteral
    | string                                                         #stringLiteral
    | NULL                                                           #nullLiteral
    | TRUE                                                           #booleanTrue
    | FALSE                                                          #booleanFalse
    | INTERVAL expression identifier                                 #intervalLiteral
    | DATE string                                                    #dateLiteral
    | TIMESTAMP string                                               #timestampLiteral
    | DEFAULT                                                        #defaultLiteral
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

windowReference
    : windowSpec
    | windowName
    ;

qualifyClause
    : QUALIFY expression
    ;

windowClause
    : WINDOW namedWindow (COMMA namedWindow)*
    ;

namedWindow
    : windowName AS windowSpec
    ;

windowName
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    ;

windowSpec
    : LPAREN baseWindow=windowName? (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
    ;

windowFrame
    : (ROWS | RANGE) (BETWEEN windowFrameBound AND windowFrameBound | windowFrameBound)
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
    | IF
    | CAST
    | VALUES
    | DEFAULT
    ;

expressionList
    : expression (COMMA expression)*
    ;

// ============ DML Statements ============

insertStatement
    : ctes? INSERT (INTO | OVERWRITE) TABLE? multipartIdentifier
      (PARTITION partitionSpec)?
      (IF NOT EXISTS)?
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)*)
    ;

insertDirectoryStatement
    : INSERT OVERWRITE LOCAL? DIRECTORY string directoryFormat? query
    ;

directoryFormat
    : STORED AS identifier
    | ROW FORMAT directoryRowFormat
    | STORED AS INPUTFORMAT string OUTPUTFORMAT string
    ;

directoryRowFormat
    : identifier
    | DELIMITED directoryDelimitedOption*
    ;

directoryDelimitedOption
    : FIELDS TERMINATED BY string
    | COLLECTION ITEMS TERMINATED BY string
    | MAP KEYS TERMINATED BY string
    | LINES TERMINATED BY string
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

mergeStatement
    : MERGE INTO target=multipartIdentifier targetAlias=tableAlias
      USING mergeSource
      ON expression
      mergeClause+
    ;

mergeSource
    : multipartIdentifier tableAlias                                  #mergeTableSource
    | LPAREN query RPAREN tableAlias                                  #mergeQuerySource
    ;

mergeClause
    : WHEN MATCHED (AND expression)? THEN UPDATE SET assignmentList    #mergeMatchedUpdate
    | WHEN MATCHED (AND expression)? THEN DELETE                       #mergeMatchedDelete
    | WHEN NOT MATCHED (AND expression)? THEN INSERT (LPAREN columnList=identifierList RPAREN)? VALUES LPAREN expressionList RPAREN #mergeNotMatchedInsert
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
      (LPAREN tableDefinitionList RPAREN)?
      commentClause?
      (PARTITIONED BY (LPAREN (partitionColumns=tableElementList | partitionColumnNames=identifierList) RPAREN
      | SPEC partitionTransformSpec))?
      clusteringClause?
      skewedClause?
      writeOrderClause?
      tableStorageFormat*
      (LOCATION string)?
      (TBLPROPERTIES LPAREN propertyList RPAREN)?
      (AS query)?
    | CREATE TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

partitionTransformSpec
    : LPAREN partitionTransform (COMMA partitionTransform)* RPAREN
    ;

partitionTransform
    : identifier
    | (YEAR | MONTH | DAY | HOUR) LPAREN identifier RPAREN
    | (TRUNCATE | BUCKET) LPAREN number COMMA identifier RPAREN
    ;

tableStorageFormat
    : STORED AS identifier
    | STORED AS INPUTFORMAT string OUTPUTFORMAT string
    | STORED BY (string | identifier) (WITH SERDEPROPERTIES LPAREN propertyList RPAREN)?
    | ROW FORMAT SERDE string (WITH SERDEPROPERTIES LPAREN propertyList RPAREN)?
    | ROW FORMAT DELIMITED directoryDelimitedOption*
    | ROW FORMAT identifier
    ;

writeOrderClause
    : WRITE LOCALLY? ORDERED BY
      (ZORDER LPAREN identifierList RPAREN | writeOrderColumn (COMMA writeOrderColumn)*)
    ;

writeOrderColumn
    : identifier (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

clusteringClause
    : CLUSTERED BY LPAREN bucketColumns=identifierList RPAREN
      (SORTED BY LPAREN sortColumn (COMMA sortColumn)* RPAREN)?
      INTO number BUCKETS
    ;

sortColumn
    : identifier (ASC | DESC)?
    ;

skewedClause
    : SKEWED BY LPAREN skewColumns=identifierList RPAREN
      ON LPAREN skewedValue (COMMA skewedValue)* RPAREN
      (STORED AS DIRECTORIES)?
    ;

skewedValue
    : LPAREN expressionList RPAREN
    | expression
    ;

createViewStatement
    : CREATE (OR REPLACE)? TEMPORARY? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
    ;

createMaterializedViewStatement
    : CREATE MATERIALIZED VIEW (IF NOT EXISTS)? multipartIdentifier
      (DISABLE REWRITE)?
      commentClause?
      tableStorageFormat*
      (LOCATION string)?
      (TBLPROPERTIES LPAREN propertyList RPAREN)?
      AS query
    ;

dropViewStatement
    : DROP MATERIALIZED? VIEW (IF EXISTS)? multipartIdentifier
    ;

alterViewStatement
    : ALTER VIEW target=multipartIdentifier AS query                              #alterViewAsQuery
    | ALTER VIEW target=multipartIdentifier RENAME TO replacement=multipartIdentifier #alterViewRename
    | ALTER VIEW target=multipartIdentifier SET TBLPROPERTIES LPAREN propertyList RPAREN #alterViewProperties
    | ALTER MATERIALIZED VIEW target=multipartIdentifier (ENABLE | DISABLE) REWRITE #alterMaterializedViewRewrite
    ;

createDatabaseStatement
    : CREATE (DATABASE | SCHEMA) (IF NOT EXISTS)? identifier databaseProperty*
    | CREATE REMOTE (DATABASE | SCHEMA) (IF NOT EXISTS)? identifier
      (COMMENT string)? USING identifier (WITH DBPROPERTIES LPAREN propertyList RPAREN)?
    ;

databaseProperty
    : COMMENT string
    | LOCATION string
    | MANAGEDLOCATION string
    | TBLPROPERTIES LPAREN propertyList RPAREN
    | WITH? DBPROPERTIES LPAREN propertyList RPAREN
    ;

alterDatabaseStatement
    : ALTER (DATABASE | SCHEMA) identifier SET alterDatabaseAction
    ;

alterDatabaseAction
    : (LOCATION | MANAGEDLOCATION) string
    | DBPROPERTIES LPAREN propertyList RPAREN
    | OWNER (USER | ROLE)? identifier
    ;

dropDatabaseStatement
    : DROP (DATABASE | SCHEMA) (IF EXISTS)? identifier (CASCADE | RESTRICT)?
    ;

connectorStatement
    : CREATE CONNECTOR (IF NOT EXISTS)? identifier TYPE string URL string
      (COMMENT string)? (WITH DCPROPERTIES LPAREN propertyList RPAREN)?
    | ALTER CONNECTOR identifier SET (DCPROPERTIES LPAREN propertyList RPAREN
      | URL string | OWNER (USER | ROLE) identifier)
    | DROP CONNECTOR (IF EXISTS)? identifier
    ;

scheduledQueryStatement
    : CREATE SCHEDULED QUERY identifier scheduleSpec scheduleOwner? scheduleEnabled? scheduledDefinition
    | ALTER SCHEDULED QUERY identifier (scheduleSpec | scheduleOwner | scheduleEnabled | scheduledDefinition | EXECUTE)
    | DROP SCHEDULED QUERY identifier
    ;

scheduleSpec
    : CRON string
    | EVERY number? (SECOND | MINUTE | HOUR | HOURS | DAY | DAYS) ((AT | OFFSET BY) string)?
    ;

scheduleOwner
    : EXECUTED AS string
    ;

scheduleEnabled
    : ENABLE | ENABLED | DISABLE | DISABLED
    ;

scheduledDefinition
    : DEFINED? AS (explainedStatement | analyzeTableStatement)
    ;

useStatement
    : USE identifier
    ;

setStatement
    : SET .+?
    ;

resetStatement
    : RESET .+?
    ;

dfsStatement
    : DFS .+?
    ;

resourceStatement
    : ADD (JAR | FILE | ARCHIVE) .+?
    | DELETE (JAR | FILE | ARCHIVE) .+?
    | LIST (JAR | FILE | ARCHIVE)
    ;

functionStatement
    : CREATE TEMPORARY? FUNCTION qualifiedName AS string (USING (JAR | FILE | ARCHIVE) string (COMMA (JAR | FILE | ARCHIVE) string)*)? #createFunction
    | DROP TEMPORARY? FUNCTION (IF EXISTS)? qualifiedName                                                        #dropFunction
    | RELOAD FUNCTION                                                                                            #reloadFunction
    ;

roleStatement
    : CREATE ROLE identifier
    | DROP ROLE identifier
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifier PURGE?
    ;

truncateTableStatement
    : TRUNCATE TABLE multipartIdentifier (PARTITION partitionSpec)?
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME (TO)? multipartIdentifier   #alterTableRename
    | ALTER TABLE multipartIdentifier
      ((PARTITION partitionSpec)? ADD COLUMNS LPAREN columns=tableElementList RPAREN (CASCADE | RESTRICT)?
      | ADD COLUMN? column=identifier dataType)                              #alterTableAddColumn
    | ALTER TABLE multipartIdentifier (PARTITION partitionSpec)? CHANGE COLUMN?
      oldColumn=identifier newColumn=identifier dataType commentClause?
      (FIRST | AFTER afterColumn=identifier)? (CASCADE | RESTRICT)?       #alterTableChangeColumn
    | ALTER TABLE multipartIdentifier (PARTITION partitionSpec)? REPLACE COLUMNS
      LPAREN columns=tableElementList RPAREN (CASCADE | RESTRICT)?       #alterTableReplaceColumns
    | ALTER TABLE target=multipartIdentifier EXCHANGE PARTITION partitionSpec WITH TABLE source=multipartIdentifier #alterTableExchangePartition
    | ALTER TABLE multipartIdentifier alterTableAction                   #alterTableOther
    ;

alterTableAction
    : ADD (IF NOT EXISTS)? PARTITION partitionSpec (LOCATION string)? (PARTITION partitionSpec (LOCATION string)?)*
    | DROP (IF EXISTS)? PARTITION partitionSpec PURGE?
    | PARTITION partitionSpec RENAME TO PARTITION partitionSpec
    | PARTITION partitionSpec SET LOCATION string
    | TOUCH (PARTITION partitionSpec)?
    | PARTITION partitionSpec TOUCH
    | CONCATENATE
    | PARTITION partitionSpec CONCATENATE
    | (PARTITION partitionSpec)? COMPACT string (AND WAIT)?
      (WITH OVERWRITE TBLPROPERTIES LPAREN propertyList RPAREN)?
    | (ARCHIVE | UNARCHIVE) PARTITION partitionSpec
    | clusteringClause
    | DROP COLUMN identifier
    | (PARTITION partitionSpec)? SET FILEFORMAT identifier
    | (PARTITION partitionSpec)? SET SERDE string (WITH SERDEPROPERTIES LPAREN propertyList RPAREN)?
    | (PARTITION partitionSpec)? SET SERDEPROPERTIES LPAREN propertyList RPAREN
    | SET PARTITION SPEC partitionTransformSpec
    | icebergSnapshotMaintenance
    | SET TBLPROPERTIES LPAREN propertyList RPAREN
    | UNSET TBLPROPERTIES LPAREN stringList RPAREN
    | SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

icebergSnapshotMaintenance
    : EXECUTE ROLLBACK LPAREN (string | number) RPAREN
    | EXECUTE EXPIRE_SNAPSHOTS (LPAREN expression RPAREN
        | BETWEEN icebergTimestamp AND icebergTimestamp
        | RETAIN LAST number)?
    | EXECUTE SET_CURRENT_SNAPSHOT LPAREN expression RPAREN
    | EXECUTE FAST_FORWARD string string?
    | EXECUTE CHERRY_PICK number
    | EXECUTE DELETE ORPHAN_FILES (OLDER THAN LPAREN string RPAREN)?
    ;

icebergTimestamp
    : string
    | LPAREN expression RPAREN
    ;

showStatement
    : SHOW CREATE TABLE multipartIdentifier                          #showTableMetadata
    | SHOW CREATE VIEW multipartIdentifier                           #showTableMetadata
    | SHOW TBLPROPERTIES multipartIdentifier (LPAREN string RPAREN)? #showTableMetadata
    | SHOW PARTITIONS multipartIdentifier (PARTITION partitionSpec)? whereClause?
      (ORDER BY sortItem (COMMA sortItem)*)? (LIMIT number)?            #showPartitionMetadata
    | SHOW SORTED? COLUMNS (FROM | IN) multipartIdentifier
      ((FROM | IN) namespace=identifier)? (LIKE? string)?              #showColumnsMetadata
    | SHOW FORMATTED? (INDEX | INDEXES) ON multipartIdentifier       #showTableMetadata
    | SHOW TABLE EXTENDED ((FROM | IN) namespace=identifier)? LIKE string (PARTITION partitionSpec)? #showNamedPartitionMetadata
    | SHOW TABLES ((FROM | IN) identifier)? (LIKE string)?           #showGenericMetadata
    | SHOW VIEWS ((FROM | IN) identifier)? (LIKE string)?            #showGenericMetadata
    | SHOW LOCKS target=multipartIdentifier? EXTENDED?               #showLockMetadata
    | SHOW GRANT (USER | ROLE)? identifier ON privilegeObject        #showGrantMetadata
    | SHOW CURRENT? ROLES                                            #showGenericMetadata
    | SHOW ROLE GRANT (USER | ROLE) identifier                       #showGenericMetadata
    | SHOW PRINCIPALS                                                #showGenericMetadata
    | SHOW COMPACTIONS
      (COMPACTIONID EQ number
      | ((DATABASE | SCHEMA) identifier | table=multipartIdentifier (PARTITION partitionSpec)?)?
        (POOL string)? (TYPE string)? (STATUS string)?
        (ORDER BY sortItem (COMMA sortItem)*)? (LIMIT number)?)        #showCompactionMetadata
    | SHOW TRANSACTIONS                                              #showGenericMetadata
    | SHOW CONF string                                               #showGenericMetadata
    | SHOW CONNECTORS                                                #showGenericMetadata
    | SHOW .+? TABLE multipartIdentifier                             #showTableMetadata
    | SHOW .+?                                                       #showGenericMetadata
    ;

describeStatement
    : (DESCRIBE | DESC) (DATABASE | SCHEMA | CONNECTOR | FUNCTION) EXTENDED? identifier
    | (DESCRIBE | DESC) TABLE? (EXTENDED | FORMATTED)? table=multipartIdentifier
      (PARTITION partitionSpec)? describedColumn=multipartIdentifier?
    | (DESCRIBE | DESC) .+?
    ;

commentStatement
    : COMMENT ON (TABLE multipartIdentifier | COLUMN multipartIdentifier)
      IS string
    ;

loadDataStatement
    : LOAD DATA LOCAL? INPATH string (OVERWRITE)? INTO TABLE multipartIdentifier
      (PARTITION partitionSpec)?
    ;

exportTableStatement
    : EXPORT TABLE multipartIdentifier (PARTITION partitionSpec)? TO string (FOR REPLICATION LPAREN string RPAREN)?
    ;

importTableStatement
    : IMPORT (EXTERNAL? TABLE multipartIdentifier (PARTITION partitionSpec)?)? FROM string (LOCATION string)?
    ;

repairTableStatement
    : MSCK? REPAIR TABLE multipartIdentifier (SYNC PARTITIONS? | PARTITIONS)?
    ;

analyzeTableStatement
    : ANALYZE TABLE multipartIdentifier (PARTITION partitionSpec)?
      (COMPUTE STATISTICS (NOSCAN | FOR ALL COLUMNS | FOR COLUMNS identifierList?)? | CACHE METADATA)
    ;

lockTableStatement
    : LOCK TABLE multipartIdentifier (SHARED | EXCLUSIVE)
    | UNLOCK TABLE multipartIdentifier
    ;

grantStatement
    : GRANT privilegeList ON privilegeObject TO identifierList (WITH GRANT OPTION)?
    ;

revokeStatement
    : REVOKE (GRANT OPTION FOR)? privilegeList ON privilegeObject FROM identifierList
    ;

privilegeObject
    : TABLE? multipartIdentifier                                     #tablePrivilegeObject
    | (DATABASE | SCHEMA) identifier                                 #databasePrivilegeObject
    ;

privilegeList
    : privilege (COMMA privilege)*
    ;

privilege
    : ALL
    | SELECT
    | INSERT
    | UPDATE
    | DELETE
    | CREATE
    | identifier
    ;

// ============ DDL Helpers ============

partitionSpec
    : LPAREN partitionVal (COMMA partitionVal)* RPAREN
    ;

partitionVal
    : identifier (EQ expression)?
    ;

tableElementList
    : tableElement (COMMA tableElement)*
    ;

tableDefinitionList
    : tableDefinition (COMMA tableDefinition)*
    ;

tableDefinition
    : tableElement
    | tableConstraint
    ;

tableElement
    : identifier dataType columnConstraint*
    ;

columnConstraint
    : NOT NULL
    | NULL
    | COMMENT string
    | DEFAULT expression
    | (CONSTRAINT identifier)? (PRIMARY KEY | UNIQUE | CHECK LPAREN expression RPAREN) constraintOption*
    ;

tableConstraint
    : (CONSTRAINT identifier)?
      (PRIMARY KEY LPAREN localColumns=identifierList RPAREN
      | UNIQUE LPAREN localColumns=identifierList RPAREN
      | CHECK LPAREN expression RPAREN)
      constraintOption*
    | (CONSTRAINT identifier)? FOREIGN KEY LPAREN localColumns=identifierList RPAREN
      REFERENCES referencedTable=multipartIdentifier LPAREN referencedColumns=identifierList RPAREN
      constraintOption*
    ;

constraintOption
    : ENABLE
    | DISABLE
    | VALIDATE
    | NOVALIDATE
    | RELY
    | NORELY
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

stringList
    : string (COMMA string)*
    ;

dataType
    : identifier (LPAREN NUMBER_LITERAL (COMMA NUMBER_LITERAL)* RPAREN)?
    | identifier LT dataTypeParameter (COMMA dataTypeParameter)* GT
    ;

dataTypeParameter
    : identifier COLON dataType
    | dataType
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
    : ADD | AFTER | ANALYZE | ARCHIVE | ASC | AUTHORIZATION | CASCADE | CAST | CHANGE | CLUSTER | COLLECTION | COLUMN | COLUMNS | COMMENT | COMPUTE | DATA | DATABASE | DATABASES | DEFAULT
    | DELIMITED | DEPENDENCY | DESCRIBE | DESC | DIRECTORY | DISTRIBUTE | END | EXCLUSIVE | EXISTS | EXPLAIN | EXTENDED | EXTERNAL | FALSE | FIELDS | FILE | FILEFORMAT | FOR | FORMAT | FORMATTED
    | DATE | DBPROPERTIES | DFS | DIV | FIRST | LAST | NULLS | FUNCTION | FUNCTIONS | GRANT | IF | INPUTFORMAT | INTERVAL | ITEMS | JAR | KEYS | LATERAL | LIKE | LIMIT | LINES | LOAD | LOCAL | LOCATION | LOCK | LOCKS | MAP | NULL | OF | OPTION | OWNER | OUT
    | EXPORT | FOLLOWING | IMPORT | INDEX | INDEXES | MATERIALIZED | MATCHED | MERGE | MSCK | NOSCAN | OFFSET | OVER | OVERWRITE | PARTITION | PARTITIONED | PARTITIONS | PRECEDING | PURGE | RANGE | REDUCE | RENAME | REWRITE
    | COMPACT | COMPACTIONS | CONCATENATE | CONF | CURRENT | EXCHANGE | OUTPUTFORMAT | PRINCIPALS | PRIVILEGES | REGEXP | RELOAD | REPAIR | REPLICATION | REPLACE | RESET | RESTRICT | REVOKE | RLIKE | ROLE | ROLES | ROW | SCHEMA | SCHEMAS | SEMI_KW | SERDE | SERDEPROPERTIES | SET | SHARED | SHOW | SORT | STATISTICS | STORED | SYNC | TABLE | TABLES | TBLPROPERTIES | TRANSACTIONS
    | CONNECTOR | CONNECTORS | DCPROPERTIES | TYPE | URL | REMOTE | COMPACTIONID | POOL | STATUS | MANAGEDLOCATION | CACHE | METADATA
    | SCHEDULED | QUERY | CRON | EVERY | EXECUTED | DEFINED | AT | SECOND | MINUTE | HOURS | DAYS | ENABLED | DISABLED
    | SYSTEM_TIME | SYSTEM_VERSION | EXECUTE | ROLLBACK | EXPIRE_SNAPSHOTS | SET_CURRENT_SNAPSHOT
    | FAST_FORWARD | CHERRY_PICK | ORPHAN_FILES | RETAIN | OLDER | THAN
    | DAY | HOUR | MONTH | SPEC | YEAR | BUCKET | BUCKETS | CLUSTERED | DIRECTORIES | DISABLE | ENABLE | KEY | NORELY | NOVALIDATE | ORDERED | WRITE | LOCALLY | ZORDER | PERCENT_KW | RELY | ROWS | SKEWED | SORTED | TABLESAMPLE | TEMPORARY | TERMINATED | TIMESTAMP | TO | TOUCH | TRANSFORM | TRUE | TRUNCATE | UNARCHIVE | UNBOUNDED | UNLOCK | UNSET | USE | USER | VALIDATE | VALUES | VIEW | WAIT | WINDOW
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
