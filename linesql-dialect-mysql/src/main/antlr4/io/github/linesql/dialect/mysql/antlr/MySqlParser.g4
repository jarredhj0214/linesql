parser grammar MySqlParser;

options { tokenVocab = MySqlLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | tableStatement                                                 #tableStmt
    | valuesStatement                                                #valuesStmt
    | insertStatement                                                #insertStmt
    | replaceStatement                                               #replaceStmt
    | loadDataStatement                                              #loadDataStmt
    | loadXmlStatement                                               #loadXmlStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | createIndexStatement                                           #createIndexStmt
    | createDatabaseStatement                                        #createDatabaseStmt
    | createTableStatement                                           #createTableStmt
    | createTablespaceStatement                                      #createTablespaceStmt
    | createViewStatement                                            #createViewStmt
    | alterDatabaseStatement                                         #alterDatabaseStmt
    | dropDatabaseStatement                                          #dropDatabaseStmt
    | dropTablespaceStatement                                        #dropTablespaceStmt
    | dropIndexStatement                                             #dropIndexStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | dropRoutineStatement                                           #dropRoutineStmt
    | dropTriggerStatement                                           #dropTriggerStmt
    | dropEventStatement                                             #dropEventStmt
    | flashbackStatement                                             #flashbackStmt
    | flashbackTenantStatement                                       #flashbackTenantStmt
    | purgeStatement                                                 #purgeStmt
    | outlineStatement                                               #outlineStmt
    | oceanBaseControlStatement                                      #oceanBaseControlStmt
    | resourceGroupStatement                                         #resourceGroupStmt
    | serverStatement                                                #serverStmt
    | pluginStatement                                                #pluginStmt
    | truncateTableStatement                                         #truncateTableStmt
    | renameTableStatement                                           #renameTableStmt
    | renameTenantStatement                                          #renameTenantStmt
    | alterTableStatement                                            #alterTableStmt
    | alterViewStatement                                             #alterViewStmt
    | alterRoutineStatement                                          #alterRoutineStmt
    | alterEventStatement                                            #alterEventStmt
    | analyzeTableStatement                                          #analyzeTableStmt
    | tableMaintenanceStatement                                      #tableMaintenanceStmt
    | explainStatement                                               #explainStmt
    | useStatement                                                   #useStmt
    | lockTablesStatement                                            #lockTablesStmt
    | unlockTablesStatement                                          #unlockTablesStmt
    | transactionStatement                                           #transactionStmt
    | setStatement                                                   #setStmt
    | doStatement                                                    #doStmt
    | callStatement                                                  #callStmt
    | prepareStatement                                               #prepareStmt
    | executeStatement                                               #executeStmt
    | deallocatePrepareStatement                                     #deallocatePrepareStmt
    | handlerStatement                                               #handlerStmt
    | createRoutineStatement                                         #createRoutineStmt
    | createTriggerStatement                                         #createTriggerStmt
    | createEventStatement                                           #createEventStmt
    | diagnosticStatement                                            #diagnosticStmt
    | accountStatement                                               #accountStmt
    | adminStatement                                                 #adminStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | commentStatement                                               #commentStmt
    ;

// ============ Query ============

tableStatement
    : TABLE multipartIdentifier queryOrganization
    ;

valuesStatement
    : valueTable queryOrganization
    ;

valueTable
    : VALUES valuesClause (COMMA valuesClause)*
    ;

handlerStatement
    : HANDLER multipartIdentifier OPEN (AS? strictIdentifier)?        #handlerOpenStatement
    | HANDLER multipartIdentifier READ handlerReadTail                #handlerReadStatement
    | HANDLER multipartIdentifier CLOSE                               #handlerCloseStatement
    ;

handlerReadTail
    : identifier handlerReadOperator LPAREN expressionList? RPAREN whereClause? (LIMIT expression)?
    | identifier handlerReadDirection whereClause? (LIMIT expression)?
    | handlerReadDirection whereClause? (LIMIT expression)?
    ;

handlerReadOperator
    : EQ | LT | GT | LTE | GTE
    ;

handlerReadDirection
    : FIRST | NEXT | PREV | LAST
    ;

query
    : ctes? queryTerm queryOrganization
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
    | INTERSECT (ALL | DISTINCT)?
    | MINUS_SET (ALL | DISTINCT)?
    ;

queryPrimary
    : querySpecification                                             #queryPrimaryDefault
    | valueTable                                                     #valuesPrimary
    | LPAREN query RPAREN                                            #subqueryPrimary
    ;

querySpecification
    : selectClause selectIntoClause? fromClause? whereClause? groupByClause? havingClause? windowClause?
    ;

selectClause
    : SELECT selectOption* setQuantifier? selectItemList
    ;

setQuantifier
    : DISTINCT
    | DISTINCTROW
    | ALL
    ;

selectOption
    : HIGH_PRIORITY
    | STRAIGHT_JOIN
    | SQL_SMALL_RESULT
    | SQL_BIG_RESULT
    | SQL_BUFFER_RESULT
    | SQL_CACHE
    | SQL_NO_CACHE
    | SQL_CALC_FOUND_ROWS
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
    : relationPrimary joinRelation*
    ;

relationPrimary
    : jsonTable tableAlias                                           #jsonTableRelation
    | multipartIdentifier flashbackClause? partitionSpec? tableAlias indexHint* #tableName
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    ;

indexHint
    : (USE | IGNORE | FORCE) (INDEX | KEY) indexHintScope? LPAREN identifierList? RPAREN
    ;

indexHintScope
    : FOR JOIN
    | FOR ORDER BY
    | FOR GROUP BY
    ;

flashbackClause
    : AS OF SNAPSHOT expression
    ;

joinRelation
    : NATURAL joinType? JOIN relationPrimary
    | joinType? (JOIN | STRAIGHT_JOIN) relationPrimary joinCriteria?
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
    : (AS? strictIdentifier (LPAREN columnAliases=identifierList RPAREN)?)?
    ;

whereClause
    : WHERE expression
    ;

groupByClause
    : GROUP BY groupByItem (COMMA groupByItem)* (WITH ROLLUP)?
    | GROUP BY ROLLUP LPAREN groupByItem (COMMA groupByItem)* RPAREN
    ;

groupByItem
    : expression (ASC | DESC)?
    ;

havingClause
    : HAVING expression
    ;

queryOrganization
    : (ORDER BY sortItem (COMMA sortItem)* (WITH ROLLUP)?)?
      procedureAnalyseClause?
      (LIMIT expression (COMMA expression)?)?
      (OFFSET expression)?
      selectIntoClause?
      lockingClause*
    ;

procedureAnalyseClause
    : PROCEDURE ANALYSE LPAREN expressionList? RPAREN
    ;

selectIntoClause
    : INTO (OUTFILE | DUMPFILE) string selectIntoOption*
    | INTO selectIntoVariableList
    ;

selectIntoVariableList
    : selectIntoVariable (COMMA selectIntoVariable)*
    ;

selectIntoVariable
    : USER_VARIABLE
    | identifier
    ;

selectIntoOption
    : (FIELDS | COLUMNS) loadDataFieldsOption+
    | LINES loadDataLinesOption+
    | CHARACTER SET identifier
    ;

lockingClause
    : FOR UPDATE lockingOption*
    | FOR SHARE lockingOption*
    | LOCK IN SHARE MODE
    ;

lockingOption
    : OF multipartIdentifierList
    | NOWAIT
    | SKIP_ LOCKED
    ;

sortItem
    : expression (ASC | DESC)?
    ;

// ============ Expressions ============

expression
    : booleanExpression
    ;

booleanExpression
    : (NOT | BANG) booleanExpression                                 #logicalNot
    | valueExpression predicate?                                     #predicatedExpr
    | left=booleanExpression (AND | LOGICAL_AND) right=booleanExpression #logicalAnd
    | left=booleanExpression XOR right=booleanExpression              #logicalXor
    | left=booleanExpression OR right=booleanExpression               #logicalOr
    | EXISTS LPAREN query RPAREN                                     #existsExpr
    ;

predicate
    : NOT? BETWEEN lower=valueExpression AND upper=valueExpression
    | NOT? IN LPAREN (expressionList | query) RPAREN
    | comparisonOperator (ALL | ANY | SOME) LPAREN query RPAREN
    | NOT? LIKE valueExpression (ESCAPE valueExpression)?
    | NOT? (REGEXP | RLIKE) valueExpression
    | SOUNDS LIKE valueExpression
    | NOT? MEMBER OF LPAREN valueExpression RPAREN
    | IS NOT? (NULL | TRUE | FALSE | UNKNOWN)
    ;

valueExpression
    : primaryExpression                                              #valueExpressionDefault
    | operator=(MINUS | PLUS | BINARY | TILDE) valueExpression       #unaryExpression
    | left=valueExpression operator=(JSON_ARROW | JSON_UNQUOTE_ARROW) string #jsonExtractExpression
    | valueExpression COLLATE identifier                             #collateExpression
    | left=valueExpression operator=(STAR | SLASH | PERCENT | DIV | MOD) right=valueExpression   #arithmeticBinary
    | left=valueExpression operator=(PLUS | MINUS) right=valueExpression             #arithmeticBinaryPlusMinus
    | left=valueExpression operator=(SHIFT_LEFT | SHIFT_RIGHT | AMP | BAR | CARET) right=valueExpression #arithmeticBinary
    | left=valueExpression CONCAT right=valueExpression              #concatExpression
    | left=valueExpression ASSIGN right=valueExpression              #assignmentExpression
    | left=valueExpression comparisonOperator right=valueExpression  #comparison
    ;

comparisonOperator
    : EQ | NEQ | NULL_SAFE_EQ | LT | GT | LTE | GTE
    ;

primaryExpression
    : CASE whenClause+ (ELSE elseExpr=expression)? END              #searchedCase
    | CASE operand=expression whenClause+ (ELSE elseExpr=expression)? END  #simpleCase
    | CAST LPAREN expression AT TIME ZONE expression AS dataType RPAREN #castAtTimeZoneExpr
    | CAST LPAREN expression AS dataType RPAREN                      #castExpr
    | CONVERT LPAREN expression USING identifier RPAREN              #convertUsingExpr
    | CONVERT LPAREN expression COMMA dataType RPAREN                #convertTypeExpr
    | TIMESTAMPADD LPAREN identifier COMMA expression COMMA expression RPAREN #timestampAddExpr
    | TIMESTAMPDIFF LPAREN identifier COMMA expression COMMA expression RPAREN #timestampDiffExpr
    | GET_FORMAT LPAREN identifier COMMA expression RPAREN             #getFormatExpr
    | CHAR LPAREN expressionList (USING identifier)? RPAREN            #charFunctionExpr
    | JSON_VALUE LPAREN expression COMMA string jsonValueReturning? jsonValueResponse* RPAREN #jsonValueExpr
    | MATCH LPAREN expressionList RPAREN AGAINST LPAREN expression fullTextSearchModifier? RPAREN #matchAgainstExpr
    | EXTRACT LPAREN identifier FROM expression RPAREN               #extractExpr
    | TRIM LPAREN trimSpec? expression? FROM expression RPAREN       #trimFromExpr
    | TRIM LPAREN expression RPAREN                                  #trimExpr
    | POSITION LPAREN expression IN expression RPAREN                #positionExpr
    | SUBSTRING LPAREN expression FROM expression (FOR expression)? RPAREN #substringFromExpr
    | functionName LPAREN STAR RPAREN nullTreatment? (OVER windowRef)? #functionCallStar
    | functionName LPAREN setQuantifier? expressionList functionOrderBy? functionSeparator? RPAREN windowValueFrom? nullTreatment? (OVER windowRef)?  #functionCall
    | functionName LPAREN RPAREN windowValueFrom? nullTreatment? (OVER windowRef)?   #functionCallEmpty
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression COMMA expressionList RPAREN                  #rowExpression
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | primaryExpression DOT identifier                                #dereference
    | identifier                                                     #columnReference
    | USER_VARIABLE                                                  #userVariable
    | number                                                         #numberLiteral
    | HEX_LITERAL                                                    #hexLiteral
    | BIT_LITERAL                                                    #bitLiteral
    | string                                                         #stringLiteral
    | (DATE | TIME | TIMESTAMP) string                               #typedStringLiteral
    | LBRACE identifier string RBRACE                                #odbcTemporalLiteral
    | currentTimeFunction                                            #currentTimeFunctionLiteral
    | NULL                                                           #nullLiteral
    | TRUE                                                           #booleanTrue
    | FALSE                                                          #booleanFalse
    | QUESTION                                                       #parameterMarker
    | INTERVAL expression identifier                                 #intervalLiteral
    | DEFAULT                                                        #defaultLiteral
    ;

currentTimeFunction
    : CURRENT_DATE
    | CURRENT_TIME
    | CURRENT_TIMESTAMP
    | CURRENT_USER
    | UTC_DATE
    | UTC_TIME
    | UTC_TIMESTAMP
    | LOCALTIME
    | LOCALTIMESTAMP
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

fullTextSearchModifier
    : IN NATURAL LANGUAGE MODE (WITH QUERY EXPANSION)?
    | IN BOOLEAN MODE
    | WITH QUERY EXPANSION
    ;

trimSpec
    : BOTH
    | LEADING
    | TRAILING
    ;

windowSpec
    : LPAREN windowInheritance? (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
    ;

windowInheritance
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    ;

windowRef
    : windowSpec
    | identifier
    ;

windowClause
    : WINDOW namedWindow (COMMA namedWindow)*
    ;

namedWindow
    : identifier AS windowSpec
    ;

windowFrame
    : (ROWS | RANGE) frameExtent
    ;

frameExtent
    : frameBound
    | BETWEEN frameBound AND frameBound
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
    | INSERT
    | LEFT
    | RIGHT
    | REPLACE
    | IF
    | CAST
    | VALUES
    | DEFAULT
    ;

jsonTable
    : JSON_TABLE LPAREN expression COMMA string COLUMNS LPAREN jsonTableColumn (COMMA jsonTableColumn)* RPAREN RPAREN
    ;

jsonTableColumn
    : identifier FOR ORDINALITY
    | identifier dataType PATH string jsonTableResponse*
    | identifier dataType EXISTS PATH string jsonTableResponse*
    | NESTED PATH string COLUMNS LPAREN jsonTableColumn (COMMA jsonTableColumn)* RPAREN
    ;

jsonTableResponse
    : (NULL | ERROR | DEFAULT string) ON (EMPTY | ERROR)
    ;

jsonValueReturning
    : RETURNING dataType
    ;

jsonValueResponse
    : (NULL | ERROR | DEFAULT string) ON (EMPTY | ERROR)
    ;

expressionList
    : expression (COMMA expression)*
    ;

functionSeparator
    : SEPARATOR expression
    ;

functionOrderBy
    : ORDER BY sortItem (COMMA sortItem)*
    ;

nullTreatment
    : (RESPECT | IGNORE) NULLS
    ;

windowValueFrom
    : FROM (FIRST | LAST)
    ;

// ============ DML Statements ============

insertStatement
    : ctes? INSERT insertPriority? IGNORE? INTO? TABLE? multipartIdentifier
      partitionSpec?
      (LPAREN columnList=identifierList? RPAREN)?
      ((VALUES | VALUE) valuesClause (COMMA valuesClause)* insertRowAlias? | SET assignmentList insertRowAlias? | tableStatement | query)
      onDuplicateKeyUpdate?
    ;

replaceStatement
    : ctes? REPLACE insertPriority? INTO? TABLE? multipartIdentifier
      partitionSpec?
      (LPAREN columnList=identifierList? RPAREN)?
      ((VALUES | VALUE) valuesClause (COMMA valuesClause)* | SET assignmentList | query)
    ;

partitionSpec
    : PARTITION LPAREN identifierList RPAREN
    ;

insertPriority
    : LOW_PRIORITY
    | DELAYED
    | HIGH_PRIORITY
    ;

loadDataStatement
    : LOAD DATA (LOW_PRIORITY | CONCURRENT)? LOCAL? INFILE string (REPLACE | IGNORE)?
      INTO TABLE multipartIdentifier partitionSpec? loadDataOption*
    ;

loadXmlStatement
    : LOAD XML (LOW_PRIORITY | CONCURRENT)? LOCAL? INFILE string (REPLACE | IGNORE)?
      INTO TABLE multipartIdentifier partitionSpec? loadXmlOption*
    ;

loadDataOption
    : (FIELDS | COLUMNS) loadDataFieldsOption+
    | LINES loadDataLinesOption+
    | IGNORE number (LINES | ROWS)
    | CHARACTER SET identifier
    | loadColumnList
    | SET assignmentList
    ;

loadXmlOption
    : CHARACTER SET identifier
    | ROWS IDENTIFIED BY string
    | IGNORE number (LINES | ROWS)
    | loadColumnList
    | SET assignmentList
    ;

loadDataFieldsOption
    : TERMINATED BY string
    | OPTIONALLY? ENCLOSED BY string
    | ESCAPED BY string
    ;

loadDataLinesOption
    : STARTING BY string
    | TERMINATED BY string
    ;

loadColumnList
    : LPAREN loadColumnRef (COMMA loadColumnRef)* RPAREN
    ;

loadColumnRef
    : identifier
    | USER_VARIABLE
    ;

onDuplicateKeyUpdate
    : ON DUPLICATE KEY UPDATE assignmentList
    ;

valuesClause
    : ROW? LPAREN expressionList? RPAREN
    ;

insertRowAlias
    : AS? identifier (LPAREN identifierList RPAREN)?
    ;

updateStatement
    : ctes? UPDATE updateModifier* relationList SET assignmentList whereClause? dmlOrganization?
    ;

deleteStatement
    : ctes? DELETE deleteModifier* FROM multipartIdentifier partitionSpec? tableAlias (USING relationList)? whereClause? dmlOrganization? #deleteFrom
    | ctes? DELETE deleteModifier* FROM deleteTargetList USING relationList whereClause? dmlOrganization?                      #deleteAliasUsing
    | ctes? DELETE deleteModifier* deleteTargetList FROM relationList whereClause? dmlOrganization?                           #deleteAlias
    ;

deleteTargetList
    : deleteTarget (COMMA deleteTarget)*
    ;

deleteTarget
    : multipartIdentifier (DOT STAR)?
    ;

updateModifier
    : LOW_PRIORITY
    | IGNORE
    ;

deleteModifier
    : LOW_PRIORITY
    | QUICK
    | IGNORE
    ;

dmlOrganization
    : ORDER BY sortItem (COMMA sortItem)* (LIMIT expression)?
    | LIMIT expression
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : multipartIdentifier EQ expression
    ;

// ============ DDL Statements ============

createTableStatement
    : CREATE TEMPORARY? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      createTableTail*
      ((IGNORE | REPLACE)? AS? query)?
    | CREATE TEMPORARY? TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

createViewStatement
    : CREATE createViewOption* (OR REPLACE)? createViewOption* VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
      viewCheckOption?
    ;

createViewOption
    : ALGORITHM EQ? (UNDEFINED | MERGE | TEMPTABLE)
    | DEFINER EQ? definerUser
    | SQL SECURITY (DEFINER | INVOKER)
    ;

definerUser
    : CURRENT_USER (LPAREN RPAREN)?
    | (identifier | string) ((AT | AT_SIGN) (identifier | string))?
    ;

viewCheckOption
    : WITH (CASCADED | LOCAL)? CHECK OPTION
    ;

createIndexStatement
    : CREATE indexType? INDEX identifier indexAlgorithm? ON multipartIdentifier LPAREN indexColumnList RPAREN indexOption*
    ;

indexType
    : UNIQUE
    | FULLTEXT
    | SPATIAL
    ;

indexOption
    : indexAlgorithm
    | KEY_BLOCK_SIZE EQ? number
    | WITH identifier identifier
    | COMMENT string
    | LOCAL
    | GLOBAL
    | PARTITION BY indexPartitionColumns PARTITIONS number
    | indexVisibility
    | ALGORITHM EQ? identifier
    | LOCK EQ? identifier
    | ENGINE_ATTRIBUTE EQ? string
    | SECONDARY_ENGINE_ATTRIBUTE EQ? string
    ;

indexPartitionColumns
    : LPAREN identifierList RPAREN
    | identifierList
    ;

indexAlgorithm
    : USING identifier
    ;

createDatabaseStatement
    : CREATE (DATABASE | SCHEMA) (IF NOT EXISTS)? identifier createDatabaseOption*
    ;

createDatabaseOption
    : DEFAULT? CHARACTER SET EQ? identifier
    | DEFAULT? CHARSET EQ? identifier
    | DEFAULT? COLLATE EQ? identifier
    | DEFAULT? ENCRYPTION EQ? string
    | READ (ONLY | WRITE)
    | DEFAULT? TABLEGROUP EQ? identifier
    ;

alterDatabaseStatement
    : ALTER (DATABASE | SCHEMA) identifier? createDatabaseOption+
    ;

createTablespaceStatement
    : CREATE UNDO? TABLESPACE identifier .+?
    ;

dropTablespaceStatement
    : DROP UNDO? TABLESPACE identifier .+?
    ;

dropDatabaseStatement
    : DROP (DATABASE | SCHEMA) (IF EXISTS)? identifier
    ;

oceanBaseControlStatement
    : ALTER PROXYCONFIG SET .+?
    | CREATE RESOURCE UNIT identifier .+?
    | ALTER RESOURCE UNIT identifier .+?
    | DROP RESOURCE UNIT identifier .+?
    | CREATE RESOURCE POOL identifier .+?
    | ALTER RESOURCE POOL identifier .+?
    | DROP RESOURCE POOL identifier .+?
    | CREATE STANDBY? TENANT identifier .+?
    | ALTER TENANT identifier .+?
    | DROP TENANT identifier .+?
    | CHANGE TENANT identifier
    | CREATE TABLEGROUP identifier (SHARDING EQ string)?
    | ALTER TABLEGROUP identifier .+?
    | DROP TABLEGROUP identifier
    | ALTER SYSTEM .+?
    | START SERVER .+?
    | STOP SERVER .+?
    ;

flashbackStatement
    : FLASHBACK TABLE multipartIdentifier TO BEFORE DROP (RENAME TO multipartIdentifier)?
    ;

flashbackTenantStatement
    : FLASHBACK TENANT identifier TO BEFORE DROP (RENAME TO identifier)?
    ;

purgeStatement
    : PURGE RECYCLEBIN
    | PURGE TABLE multipartIdentifier
    | PURGE INDEX multipartIdentifier
    | PURGE DATABASE identifier
    | PURGE TENANT identifier
    ;

dropIndexStatement
    : DROP INDEX identifier ON multipartIdentifier dropIndexOption*
    ;

dropIndexOption
    : ALGORITHM EQ? identifier
    | LOCK EQ? identifier
    ;

dropTableStatement
    : DROP TEMPORARY? TABLE (IF EXISTS)? multipartIdentifierList dropRestrictOption?
    ;

dropViewStatement
    : DROP VIEW (IF EXISTS)? multipartIdentifierList dropRestrictOption?
    ;

dropRestrictOption
    : RESTRICT
    | CASCADE
    ;

dropRoutineStatement
    : DROP (PROCEDURE | FUNCTION) (IF EXISTS)? multipartIdentifier
    ;

dropTriggerStatement
    : DROP TRIGGER (IF EXISTS)? multipartIdentifier
    ;

dropEventStatement
    : DROP EVENT (IF EXISTS)? multipartIdentifier
    ;

outlineStatement
    : CREATE (OR REPLACE)? FORMAT? OUTLINE identifier ON .+?
    | DROP FORMAT? OUTLINE identifier
    ;

resourceGroupStatement
    : CREATE RESOURCE GROUP identifier resourceGroupOption*
    | ALTER RESOURCE GROUP identifier resourceGroupOption*
    | DROP RESOURCE GROUP identifier
    | SET RESOURCE GROUP identifier (FOR number (COMMA number)*)?
    ;

resourceGroupOption
    : TYPE EQ? identifier
    | VCPU EQ? resourceGroupVcpuSpec
    | THREAD_PRIORITY EQ? number
    | ENABLE
    | DISABLE
    ;

resourceGroupVcpuSpec
    : number (MINUS number)? (COMMA number (MINUS number)?)*
    ;

serverStatement
    : CREATE SERVER identifier .+?
    | ALTER SERVER identifier .+?
    | DROP SERVER (IF EXISTS)? identifier
    ;

pluginStatement
    : INSTALL PLUGIN identifier SONAME string
    | UNINSTALL PLUGIN identifier
    | INSTALL COMPONENT string (COMMA string)*
    | UNINSTALL COMPONENT string (COMMA string)*
    ;

truncateTableStatement
    : TRUNCATE TABLE? multipartIdentifier
    ;

renameTableStatement
    : RENAME TABLE renameTablePair (COMMA renameTablePair)*
    ;

renameTenantStatement
    : RENAME TENANT identifier TO identifier
    ;

renameTablePair
    : source=multipartIdentifier TO target=multipartIdentifier
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME (TO | AS)? multipartIdentifier   #alterTableRename
    | ALTER TABLE multipartIdentifier ADD COLUMN? tableElement columnPosition? #alterTableAddColumn
    | ALTER TABLE multipartIdentifier CHANGE COLUMN? identifier identifier dataType columnConstraint* columnPosition? #alterTableOther
    | ALTER TABLE multipartIdentifier MODIFY COLUMN? identifier dataType columnConstraint* columnPosition?            #alterTableOther
    | ALTER TABLE multipartIdentifier RENAME COLUMN identifier TO identifier   #alterTableOther
    | ALTER TABLE multipartIdentifier alterTableActionList                    #alterTableOther
    ;

alterTableActionList
    : alterTableAction (COMMA alterTableAction)*
    ;

alterViewStatement
    : ALTER createViewOption* VIEW multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
      viewCheckOption?
    ;

alterRoutineStatement
    : ALTER (PROCEDURE | FUNCTION) multipartIdentifier .+?
    ;

alterEventStatement
    : ALTER EVENT multipartIdentifier .+?
    ;

alterTableAction
    : DROP COLUMN identifier
    | DROP PRIMARY KEY
    | DROP FOREIGN KEY identifier
    | DROP (KEY | INDEX) identifier
    | RENAME (KEY | INDEX) identifier TO identifier
    | ALTER (KEY | INDEX) identifier indexVisibility
    | ALTER COLUMN? identifier SET DEFAULT expression
    | ALTER COLUMN? identifier DROP DEFAULT
    | ADD PARTITION partitionDefinitionList
    | DROP PARTITION identifierList
    | TRUNCATE PARTITION identifierList
    | REBUILD PARTITION identifierList
    | CHECK PARTITION identifierList
    | OPTIMIZE PARTITION identifierList
    | ANALYZE PARTITION identifierList
    | REPAIR PARTITION identifierList
    | COALESCE PARTITION number
    | REORGANIZE PARTITION identifierList INTO partitionDefinitionList
    | REMOVE PARTITIONING
    | EXCHANGE PARTITION identifier WITH TABLE multipartIdentifier exchangeValidation?
    | ORDER BY sortItem (COMMA sortItem)*
    | FORCE
    | DISCARD TABLESPACE
    | IMPORT TABLESPACE
    | ALGORITHM EQ? identifier
    | LOCK EQ? identifier
    | ADD PRIMARY KEY indexAlgorithm? LPAREN indexColumnList RPAREN indexOption*
    | ADD indexType? (KEY | INDEX) identifier? indexAlgorithm? LPAREN indexColumnList RPAREN indexOption*
    | ADD (CONSTRAINT identifier?)? FOREIGN KEY identifier? LPAREN identifierList RPAREN referenceDefinition
    | ADD CONSTRAINT? identifier? CHECK LPAREN expression RPAREN checkEnforcement?
    | DROP CHECK identifier
    | ALTER CHECK identifier checkEnforcement
    | CONVERT TO CHARACTER SET identifier (COLLATE identifier)?
    | DEFAULT? CHARACTER SET EQ? identifier (COLLATE EQ? identifier)?
    | DEFAULT? CHARSET EQ? identifier (COLLATE EQ? identifier)?
    | DEFAULT? COLLATE EQ? identifier
    | (ENABLE | DISABLE) KEYS
    | ADD COLUMN? tableElement columnPosition?
    | ADD COLUMN? LPAREN tableElementList RPAREN
    | SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

checkEnforcement
    : NOT? ENFORCED
    ;

columnPosition
    : FIRST
    | AFTER identifier
    ;

analyzeTableStatement
    : ANALYZE writeToBinlogOption? TABLE multipartIdentifierList analyzeTableOption?
    ;

analyzeTableOption
    : UPDATE HISTOGRAM ON identifierList (WITH number BUCKETS)? (USING DATA string)?
    | DROP HISTOGRAM ON identifierList
    ;

tableMaintenanceStatement
    : CHECK TABLE multipartIdentifierList tableMaintenanceOption*
    | CHECKSUM TABLE multipartIdentifierList tableMaintenanceOption*
    | (OPTIMIZE | REPAIR) writeToBinlogOption? TABLE multipartIdentifierList tableMaintenanceOption*
    ;

writeToBinlogOption
    : NO_WRITE_TO_BINLOG
    | LOCAL
    ;

tableMaintenanceOption
    : FOR UPGRADE
    | QUICK
    | FAST
    | MEDIUM
    | EXTENDED
    | CHANGED
    | USE_FRM
    ;

explainStatement
    : EXPLAIN explainOption* FOR CONNECTION number
    | EXPLAIN explainOption* TABLE? multipartIdentifier identifier?
    | EXPLAIN explainOption* ANALYZE? statement
    | (DESCRIBE | DESC) explainOption* ANALYZE? (query | insertStatement | replaceStatement | updateStatement | deleteStatement)
    ;

explainOption
    : FORMAT EQ identifier
    | BASIC
    | OUTLINE
    | EXTENDED
    | EXTENDED_NOADDR
    | PARTITIONS
    ;

useStatement
    : USE identifier
    ;

lockTablesStatement
    : LOCK TABLES lockTable (COMMA lockTable)*
    ;

lockTable
    : multipartIdentifier tableAlias lockMode
    ;

lockMode
    : READ LOCAL?
    | LOW_PRIORITY WRITE
    | WRITE
    ;

unlockTablesStatement
    : UNLOCK TABLES
    ;

setStatement
    : SET setElement (COMMA setElement)*
    ;

setElement
    : (GLOBAL | SESSION | LOCAL)? setVariable (EQ | ASSIGN)? setValue
    | NAMES (identifier | DEFAULT) (COLLATE identifier)?
    | (CHARACTER SET | CHARSET) (identifier | DEFAULT)
    ;

setVariable
    : identifier
    | USER_VARIABLE
    ;

setValue
    : expression
    | USER_VARIABLE
    | DEFAULT
    ;

transactionStatement
    : START TRANSACTION transactionCharacteristicList?
    | SET (GLOBAL | SESSION)? TRANSACTION transactionCharacteristicList
    | BEGIN WORK?
    | COMMIT WORK? completionOption*
    | ROLLBACK WORK? completionOption*
    | ROLLBACK WORK? TO SAVEPOINT? identifier
    | SAVEPOINT identifier
    | RELEASE SAVEPOINT identifier
    | XA START xaXid (JOIN | RESUME)?
    | XA END xaXid (SUSPEND (FOR MIGRATE)?)?
    | XA PREPARE xaXid
    | XA COMMIT xaXid (ONE PHASE)?
    | XA ROLLBACK xaXid
    | XA RECOVER (CONVERT XID)?
    ;

xaXid
    : expression (COMMA expression (COMMA expression)?)?
    ;

doStatement
    : DO expressionList
    ;

transactionCharacteristicList
    : transactionCharacteristic (COMMA transactionCharacteristic)*
    ;

transactionCharacteristic
    : WITH CONSISTENT SNAPSHOT
    | ISOLATION LEVEL isolationLevel
    | READ (WRITE | ONLY)
    ;

isolationLevel
    : READ (UNCOMMITTED | COMMITTED)
    | REPEATABLE READ
    | SERIALIZABLE
    ;

completionOption
    : AND NO? CHAIN
    | NO? RELEASE
    ;

callStatement
    : CALL multipartIdentifier LPAREN expressionList? RPAREN
    ;

prepareStatement
    : PREPARE identifier FROM .+?
    ;

executeStatement
    : EXECUTE identifier (USING expressionList)?
    ;

deallocatePrepareStatement
    : (DEALLOCATE | DROP) PREPARE identifier
    ;

createRoutineStatement
    : CREATE (DEFINER EQ expression)? (PROCEDURE | FUNCTION) multipartIdentifier .+?
    ;

createTriggerStatement
    : CREATE (DEFINER EQ expression)? TRIGGER multipartIdentifier
      (BEFORE | AFTER) (INSERT | UPDATE | DELETE)
      ON multipartIdentifier FOR EACH ROW .+?
    ;

createEventStatement
    : CREATE EVENT multipartIdentifier
      ON SCHEDULE eventSchedule
      eventOption*
      DO eventBodyStatement
    ;

eventSchedule
    : AT expression
    | EVERY expression identifier (STARTS expression)? (ENDS expression)?
    ;

eventOption
    : ON COMPLETION NOT? PRESERVE
    | ENABLE
    | DISABLE (ON (SLAVE | REPLICA))?
    | COMMENT string
    ;

eventBodyStatement
    : insertStatement
    | replaceStatement
    | updateStatement
    | deleteStatement
    | .+?
    ;

accountStatement
    : CREATE USER (IF NOT EXISTS)? userAccountSpec (COMMA userAccountSpec)* accountOption*
    | ALTER USER (IF EXISTS)? userAccountSpec (COMMA userAccountSpec)* accountOption*
    | DROP USER (IF EXISTS)? roleNameList
    | CREATE ROLE (IF NOT EXISTS)? roleNameList
    | DROP ROLE (IF EXISTS)? roleNameList
    | SET ROLE .+?
    | GRANT .+?
    | REVOKE .+?
    ;

userAccountSpec
    : roleName authOption*
    ;

authOption
    : IDENTIFIED (WITH identifier)? (BY string | AS string)?
    | IDENTIFIED BY PASSWORD string
    ;

accountOption
    : REQUIRE (NONE | SSL | X509 | CIPHER string | ISSUER string | SUBJECT string)+
    | PASSWORD EXPIRE (DEFAULT | NEVER | INTERVAL number identifier)?
    | ACCOUNT (LOCK | UNLOCK)
    | COMMENT string
    | ATTRIBUTE string
    | FAILED_LOGIN_ATTEMPTS number
    | PASSWORD_LOCK_TIME (number | UNBOUNDED)
    ;

roleNameList
    : roleName (COMMA roleName)*
    ;

roleName
    : identifier ((AT | AT_SIGN) (identifier | string))?
    | string ((AT | AT_SIGN) (identifier | string))?
    ;

adminStatement
    : FLUSH (TABLES | PRIVILEGES | STATUS | LOGS | .+?)
    | KILL (CONNECTION | QUERY)? number
    | LOCK INSTANCE FOR BACKUP
    | UNLOCK INSTANCE
    | CLONE cloneTarget
    | START (REPLICA | SLAVE) .*?
    | STOP (REPLICA | SLAVE) .*?
    | RESET (REPLICA | SLAVE) ALL?
    | CHANGE (REPLICATION SOURCE | MASTER) TO .+?
    | RESET .+?
    ;

diagnosticStatement
    : GET (CURRENT | STACKED)? DIAGNOSTICS .+?
    | SIGNAL .+?
    | RESIGNAL .+?
    ;

cloneTarget
    : LOCAL DATA DIRECTORY EQ? string
    | INSTANCE FROM cloneDonor IDENTIFIED BY string DATA DIRECTORY EQ? string
    ;

cloneDonor
    : string ((AT | AT_SIGN) string)? (COLON number)?
    | identifier ((AT | AT_SIGN) identifier)? (COLON number)?
    ;

showStatement
    : showCreateStatement
    | showColumnsStatement
    | showIndexStatement
    | showTableStatusStatement
    | showDiagnosticStatement
    | showLogEventsStatement
    | SHOW BINARY LOGS
    | SHOW MASTER STATUS
    | SHOW (REPLICA | SLAVE) STATUS
    | SHOW FULL? TABLES ((FROM | IN) identifier)? showFilter?
    | SHOW (GLOBAL | SESSION)? (VARIABLES | STATUS) showFilter?
    | SHOW RECYCLEBIN showFilter?
    | SHOW OPEN TABLES ((FROM | IN) identifier)? showFilter?
    | SHOW (TRIGGERS | EVENTS) showFromSchema? showFilter?
    | SHOW (PROCEDURE | FUNCTION) STATUS showFilter?
    | SHOW (DATABASES | SCHEMAS | GRANTS | PROCESSLIST | ENGINES | COLLATION | COLLATIONS | CHARACTER SET)
    | SHOW showObject (FROM | IN) multipartIdentifier
    | SHOW .+? (TABLE | VIEW) multipartIdentifier
    | SHOW .+?
    ;

showDiagnosticStatement
    : SHOW (identifier LPAREN STAR RPAREN)? (WARNINGS | ERRORS)
    ;

showLogEventsStatement
    : SHOW (BINLOG | RELAYLOG) EVENTS (IN string)? (FROM number)? (LIMIT number (COMMA number)?)?
    ;

showCreateStatement
    : SHOW CREATE (DATABASE | SCHEMA) identifier
    | SHOW CREATE (TABLE | VIEW) multipartIdentifier
    | SHOW CREATE (PROCEDURE | FUNCTION | TRIGGER | EVENT) multipartIdentifier
    | SHOW CREATE USER roleName
    ;

showColumnsStatement
    : SHOW EXTENDED? FULL? (COLUMNS | FIELDS) (FROM | IN) multipartIdentifier showFromSchema? showFilter?
    ;

showIndexStatement
    : SHOW EXTENDED? (INDEX | INDEXES | KEY | KEYS) (FROM | IN) multipartIdentifier showFromSchema? showFilter?
    ;

showTableStatusStatement
    : SHOW TABLE STATUS showFromSchema? showFilter?
    ;

showFromSchema
    : (FROM | IN) identifier
    ;

showFilter
    : LIKE string
    | WHERE expression
    ;

showObject
    : identifier
    | INDEX
    | INDEXES
    | KEY
    | KEYS
    ;

describeStatement
    : (DESCRIBE | DESC) TABLE? multipartIdentifier identifier?
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
    : identifier dataType columnConstraint*
    | tableConstraint
    ;

columnConstraint
    : NOT NULL
    | NULL
    | COMMENT string
    | DEFAULT expression
    | ON UPDATE expression
    | AUTO_INCREMENT
    | PRIMARY KEY
    | UNIQUE KEY?
    | (CONSTRAINT identifier?)? CHECK LPAREN expression RPAREN checkEnforcement?
    | VISIBLE
    | INVISIBLE
    | COLUMN_FORMAT identifier
    | STORAGE identifier
    | SRID number
    | GENERATED ALWAYS? AS LPAREN expression RPAREN (VIRTUAL | STORED)?
    | AS LPAREN expression RPAREN (VIRTUAL | STORED)?
    | referenceDefinition
    ;

tableConstraint
    : (CONSTRAINT identifier)? PRIMARY KEY indexAlgorithm? LPAREN indexColumnList RPAREN indexOption*
    | (CONSTRAINT identifier)? indexType? (KEY | INDEX) identifier? indexAlgorithm? LPAREN indexColumnList RPAREN indexOption*
    | (CONSTRAINT identifier)? FOREIGN KEY identifier? LPAREN identifierList RPAREN referenceDefinition
    | (CONSTRAINT identifier)? CHECK LPAREN expression RPAREN checkEnforcement?
    ;

referenceDefinition
    : REFERENCES multipartIdentifier (LPAREN identifierList RPAREN)? referenceOption*
    ;

exchangeValidation
    : (WITH | WITHOUT) VALIDATION
    ;

referenceOption
    : MATCH identifier
    | ON (DELETE | UPDATE) referenceAction
    ;

referenceAction
    : RESTRICT
    | CASCADE
    | SET NULL
    | NO ACTION
    | SET DEFAULT
    ;

indexColumnList
    : indexColumn (COMMA indexColumn)*
    ;

indexColumn
    : identifier (LPAREN NUMBER_LITERAL RPAREN)? (ASC | DESC)?
    | LPAREN expression RPAREN (ASC | DESC)?
    ;

indexVisibility
    : VISIBLE
    | INVISIBLE
    ;

commentClause
    : COMMENT string
    ;

createTableTail
    : tableOption
    | partitionClause
    ;

propertyList
    : property (COMMA property)*
    ;

property
    : string EQ string
    ;

tableOption
    : ENGINE EQ? identifier
    | DEFAULT? CHARSET EQ? identifier
    | DEFAULT? CHARACTER SET EQ? identifier
    | COLLATE EQ? identifier
    | AUTO_INCREMENT EQ? number
    | AVG_ROW_LENGTH EQ? number
    | MAX_ROWS EQ? number
    | MIN_ROWS EQ? number
    | DELAY_KEY_WRITE EQ? (identifier | number)
    | INSERT_METHOD EQ? identifier
    | ROW_FORMAT EQ? identifier
    | PACK_KEYS EQ? (identifier | number)
    | STATS_PERSISTENT EQ? (identifier | number)
    | STATS_AUTO_RECALC EQ? (identifier | number)
    | STATS_SAMPLE_PAGES EQ? (identifier | number)
    | KEY_BLOCK_SIZE EQ? number
    | COMPRESSION EQ? string
    | TABLESPACE identifier
    | DATA DIRECTORY EQ? string
    | INDEX DIRECTORY EQ? string
    | CHECKSUM EQ? number
    | TABLE_CHECKSUM EQ? number
    | PASSWORD EQ? string
    | CONNECTION EQ? string
    | ENGINE_ATTRIBUTE EQ? string
    | SECONDARY_ENGINE_ATTRIBUTE EQ? string
    | AUTOEXTEND_SIZE EQ? number
    | COMMENT EQ? string
    | TABLEGROUP EQ? identifier
    | UNION EQ? LPAREN multipartIdentifierList RPAREN
    ;

partitionClause
    : PARTITION BY partitionMethod partitionExpression partitionCount? subpartitionClause? partitionDefinitionList?
    ;

partitionMethod
    : LINEAR? (RANGE | HASH | KEY | LIST)
    ;

partitionExpression
    : (ALGORITHM EQ? number)? COLUMNS? LPAREN identifierList RPAREN
    ;

partitionCount
    : PARTITIONS number
    ;

subpartitionClause
    : SUBPARTITION BY LINEAR? (HASH | KEY) (ALGORITHM EQ? number)? LPAREN identifierList RPAREN (SUBPARTITIONS number)?
    ;

partitionDefinitionList
    : LPAREN partitionDefinition (COMMA partitionDefinition)* RPAREN
    ;

partitionDefinition
    : PARTITION identifier VALUES LESS THAN LPAREN literalValueList RPAREN tableOption* subpartitionDefinitionList?
    | PARTITION identifier VALUES LESS THAN MAXVALUE tableOption* subpartitionDefinitionList?
    | PARTITION identifier VALUES IN LPAREN partitionValueList RPAREN tableOption* subpartitionDefinitionList?
    ;

subpartitionDefinitionList
    : LPAREN subpartitionDefinition (COMMA subpartitionDefinition)* RPAREN
    ;

subpartitionDefinition
    : SUBPARTITION identifier tableOption*
    ;

partitionValueList
    : partitionValue (COMMA partitionValue)*
    ;

partitionValue
    : literalValue
    | LPAREN literalValueList RPAREN
    ;

literalValueList
    : literalValue (COMMA literalValue)*
    ;

literalValue
    : number
    | HEX_LITERAL
    | BIT_LITERAL
    | string
    | identifier
    ;

dataType
    : DOUBLE PRECISION dataTypeAttribute*
    | (SIGNED | UNSIGNED) INTEGER? dataTypeAttribute*
    | NATIONAL? (CHARACTER | CHAR | VARCHAR) VARYING? (LPAREN dataTypeArgument (COMMA dataTypeArgument)* RPAREN)? dataTypeAttribute*
    | (NCHAR | NVARCHAR) VARYING? (LPAREN dataTypeArgument (COMMA dataTypeArgument)* RPAREN)? dataTypeAttribute*
    | identifier (LPAREN dataTypeArgument (COMMA dataTypeArgument)* RPAREN)? dataTypeAttribute*
    | identifier LT dataType (COMMA dataType)* GT dataTypeAttribute*
    ;

dataTypeArgument
    : NUMBER_LITERAL
    | string
    | identifier
    ;

dataTypeAttribute
    : UNSIGNED
    | ZEROFILL
    | ARRAY
    | CHARACTER SET identifier
    | CHARSET identifier
    | COLLATE identifier
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

multipartIdentifierList
    : multipartIdentifier (COMMA multipartIdentifier)*
    ;

identifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | TEMPLATE_VARIABLE
    | nonReservedKeyword
    ;

strictIdentifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | TEMPLATE_VARIABLE
    | nonReservedKeyword
    ;

nonReservedKeyword
    : ACCOUNT | ACTION | ADD | AFTER | AGAINST | ALGORITHM | ANALYSE | ANALYZE | ANY | ARRAY | ASC | AT | ATTRIBUTE | AUTO_INCREMENT | BACKUP | BASIC | BINARY | BINLOG | BOOLEAN | BOTH | CASCADE | CASCADED | CALL | CAST | CHANGE | CHAR | CHARACTER | CHARSET | CHECK | CHECKSUM | CIPHER | CLONE | CLOSE | COALESCE | COLLATE | COLLATION | COLLATIONS | CONNECTION
    | ALWAYS | AVG_ROW_LENGTH | COLUMN | COLUMN_FORMAT | COMMENT | CONSTRAINT | CURRENT | CURRENT_DATE | CURRENT_TIME | CURRENT_TIMESTAMP | CURRENT_USER | DATA | DATABASES | DATE | DEFAULT | DEFINER | DELAYED | DELAY_KEY_WRITE | DELETE | DESCRIBE | DESC | DISTINCTROW | DO | DUPLICATE | EACH | ENCLOSED | END | ENGINES
    | DATABASE | DATAFILE | DEALLOCATE | DIAGNOSTICS | DISCARD | DIV | DOUBLE | DUMPFILE | EMPTY | ENCRYPTION | ENFORCED | ENGINE | ENDS | ERROR | ERRORS | ESCAPE | ESCAPED | EVENT | EVENTS | EVERY | EXCHANGE | EXECUTE | EXISTS | EXPIRE | EXPLAIN | EXTERNAL | EXTENDED | EXTENDED_NOADDR | FAILED_LOGIN_ATTEMPTS | FALSE | FAST | FIELDS | FIRST | FLUSH | FOLLOWING | FOR | FORCE | FOREIGN | FORMAT | FULLTEXT | FUNCTION | GENERATED | GET | GET_FORMAT | GLOBAL | GRANT | GRANTS | GROUP | HANDLER | HIGH_PRIORITY | IF | IGNORE | IMPORT | INDEX | INSERT_METHOD | INSTANCE | INTEGER | INTERVAL | INVOKER | ISSUER | JSON_TABLE | JSON_VALUE | KEY | KILL | LAST | LATERAL | LIKE | LIMIT | LINES | LOCK | LOCKED | LOGS | MASTER | MAX_ROWS | MEDIUM | MERGE | MIN_ROWS | MINUS_SET | MOD | MODE | MODIFY | NAMES | NATIONAL | NCHAR | NESTED | NEVER | NEXT | NO | NONE | NO_WRITE_TO_BINLOG | NOWAIT | NULL | NVARCHAR | OF
    | EXPANSION | EXTRACT | INFILE | IDENTIFIED | INDEXES | KEY_BLOCK_SIZE | KEYS | LANGUAGE | LEADING | LINEAR | LOAD | LOCAL | LOCALTIME | LOCALTIMESTAMP | LOW_PRIORITY | MATCH | MEMBER | NULLS | OFFSET | ONE | OPEN | OPTIMIZE | OPTION | OPTIONALLY | ORDINALITY | OUTFILE | OUTLINE | OVER | PACK_KEYS | PARTITION | PARTITIONING | PARTITIONS | PASSWORD | PASSWORD_LOCK_TIME | PATH | PHASE | POOL | POSITION | PRECEDING | PRECISION | PREPARE | PRESERVE | PREV | PRIVILEGES | PROCEDURE | PROCESSLIST | PROXYCONFIG | QUERY | RANGE | READ | REBUILD | RECOVER | RECURSIVE | REFERENCES | RELAYLOG | REORGANIZE | REPAIR | REPLACE | RENAME | REMOVE | REPLICA | REPLICATION | REQUIRE | RESET | RESPECT | RESUME | RETURNING | ROW | ROW_FORMAT | ROWS | SCHEDULE | SECONDARY_ENGINE_ATTRIBUTE | SEPARATOR
    | BUCKETS | HASH | HISTOGRAM | LESS | LIST | MAXVALUE | NATURAL | ONLY | PRIMARY | QUICK | REGEXP | RELEASE | RESTRICT | REVOKE | RLIKE | ROLLBACK | ROLLUP | SAVEPOINT | SCHEMAS | SECURITY | SESSION | SET | SHARDING | SHARE | SHOW | SKIP_ | SNAPSHOT | SOUNDS | SOURCE | SPATIAL | SQL | SQL_BIG_RESULT | SQL_BUFFER_RESULT | SQL_CACHE | SQL_CALC_FOUND_ROWS | SSL | STOP | SUBJECT
    | RESIGNAL | SIGNED | SIGNAL | SLAVE | SOME | SQL_NO_CACHE | SQL_SMALL_RESULT | SRID | STACKED | STANDBY | START | STARTING | STARTS | STATS_AUTO_RECALC | STATS_PERSISTENT | STATS_SAMPLE_PAGES | STORAGE | STORED | STRAIGHT_JOIN | SUBPARTITION | SUBPARTITIONS | SUBSTRING | SUSPEND | SYSTEM | TABLE | TABLE_CHECKSUM | TABLEGROUP | TABLESPACE | TEMPORARY | TEMPTABLE | TENANT | TERMINATED | THAN | TIME | TIMESTAMP | TIMESTAMPADD | TIMESTAMPDIFF | TO | TRAILING | TRANSACTION | ISOLATION | LEVEL | REPEATABLE | COMMITTED | UNCOMMITTED | SERIALIZABLE | TRIM | TRUE | TRUNCATE | TYPE | UNBOUNDED | UNDEFINED | UNDO | UNIQUE | UNIT | UNKNOWN | UNSIGNED | VALIDATION | VALUES | VIEW | VIRTUAL | VISIBLE | INVISIBLE | WITHOUT | WORK | X509 | XOR | ZEROFILL | ZONE
    | AUTOEXTEND_SIZE | CHANGED | COLUMNS | COMPLETION | COMPRESSION | CONVERT | DIRECTORY | ENGINE_ATTRIBUTE | MIGRATE | SCHEMA | STATUS | TABLES | TRIGGER | TRIGGERS | UNLOCK | UPGRADE | USE | USE_FRM | USER | UTC_DATE | UTC_TIME | UTC_TIMESTAMP | VALUE | VARCHAR | VARIABLES | VARYING | WARNINGS | WINDOW | WITH | WRITE | XA | XID | XML | BEGIN | CHAIN | COMMIT | CONSISTENT
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    | CHARSET_STRING_LITERAL
    | NATIONAL_STRING_LITERAL
    ;
