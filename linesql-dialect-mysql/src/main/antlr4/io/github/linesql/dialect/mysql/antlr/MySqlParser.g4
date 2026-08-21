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
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | createIndexStatement                                           #createIndexStmt
    | createDatabaseStatement                                        #createDatabaseStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropDatabaseStatement                                          #dropDatabaseStmt
    | dropIndexStatement                                             #dropIndexStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | dropRoutineStatement                                           #dropRoutineStmt
    | dropTriggerStatement                                           #dropTriggerStmt
    | dropEventStatement                                             #dropEventStmt
    | truncateTableStatement                                         #truncateTableStmt
    | renameTableStatement                                           #renameTableStmt
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
    | setStatement                                                   #setStmt
    | transactionStatement                                           #transactionStmt
    | doStatement                                                    #doStmt
    | callStatement                                                  #callStmt
    | prepareStatement                                               #prepareStmt
    | executeStatement                                               #executeStmt
    | deallocatePrepareStatement                                     #deallocatePrepareStmt
    | createRoutineStatement                                         #createRoutineStmt
    | createTriggerStatement                                         #createTriggerStmt
    | createEventStatement                                           #createEventStmt
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
    : VALUES valuesClause (COMMA valuesClause)* queryOrganization
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
    ;

queryPrimary
    : querySpecification                                             #queryPrimaryDefault
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
    : multipartIdentifier tableAlias indexHint*                      #tableName
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    ;

indexHint
    : (USE | IGNORE | FORCE) (INDEX | KEY) indexHintScope? LPAREN identifierList RPAREN
    ;

indexHintScope
    : FOR JOIN
    | FOR ORDER BY
    | FOR GROUP BY
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
    : (AS? strictIdentifier)?
    ;

whereClause
    : WHERE expression
    ;

groupByClause
    : GROUP BY groupByItem (COMMA groupByItem)* (WITH ROLLUP)?
    ;

groupByItem
    : expression (ASC | DESC)?
    ;

havingClause
    : HAVING expression
    ;

queryOrganization
    : (ORDER BY sortItem (COMMA sortItem)*)?
      (LIMIT expression (COMMA expression)?)?
      (OFFSET expression)?
      selectIntoClause?
      lockingClause*
    ;

selectIntoClause
    : INTO (OUTFILE | DUMPFILE) string selectIntoOption*
    | INTO userVariableList
    ;

userVariableList
    : USER_VARIABLE (COMMA USER_VARIABLE)*
    ;

selectIntoOption
    : FIELDS loadDataFieldsOption+
    | LINES loadDataLinesOption+
    | CHARACTER SET identifier
    ;

lockingClause
    : FOR UPDATE
    | LOCK IN SHARE MODE
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
    | left=booleanExpression AND right=booleanExpression              #logicalAnd
    | left=booleanExpression OR right=booleanExpression               #logicalOr
    | EXISTS LPAREN query RPAREN                                     #existsExpr
    ;

predicate
    : NOT? BETWEEN lower=valueExpression AND upper=valueExpression
    | NOT? IN LPAREN (expressionList | query) RPAREN
    | NOT? LIKE valueExpression
    | NOT? (REGEXP | RLIKE) valueExpression
    | IS NOT? (NULL | TRUE | FALSE)
    ;

valueExpression
    : primaryExpression                                              #valueExpressionDefault
    | operator=(MINUS | PLUS | BINARY) valueExpression               #unaryExpression
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
    | CAST LPAREN expression AS dataType RPAREN                      #castExpr
    | functionName LPAREN STAR RPAREN (OVER windowRef)?             #functionCallStar
    | functionName LPAREN setQuantifier? expressionList functionSeparator? RPAREN (OVER windowRef)?  #functionCall
    | functionName LPAREN RPAREN (OVER windowRef)?                  #functionCallEmpty
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | primaryExpression DOT identifier                                #dereference
    | identifier                                                     #columnReference
    | USER_VARIABLE                                                  #userVariable
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

windowSpec
    : LPAREN (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
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

functionSeparator
    : SEPARATOR expression
    ;

// ============ DML Statements ============

insertStatement
    : ctes? INSERT insertPriority? IGNORE? INTO? TABLE? multipartIdentifier
      partitionSpec?
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)* insertRowAlias? | SET assignmentList)
      onDuplicateKeyUpdate?
    ;

replaceStatement
    : REPLACE insertPriority? INTO? TABLE? multipartIdentifier
      partitionSpec?
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)* | SET assignmentList)
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
    : LOAD DATA LOCAL? INFILE string INTO TABLE multipartIdentifier loadDataOption*
    ;

loadDataOption
    : FIELDS loadDataFieldsOption+
    | LINES loadDataLinesOption+
    | IGNORE number LINES
    | LPAREN identifierList RPAREN
    | SET assignmentList
    ;

loadDataFieldsOption
    : TERMINATED BY string
    | ENCLOSED BY string
    | ESCAPED BY string
    ;

loadDataLinesOption
    : STARTING BY string
    | TERMINATED BY string
    ;

onDuplicateKeyUpdate
    : ON DUPLICATE KEY UPDATE assignmentList
    ;

valuesClause
    : ROW? LPAREN expressionList RPAREN
    ;

insertRowAlias
    : AS? identifier (LPAREN identifierList RPAREN)?
    ;

updateStatement
    : ctes? UPDATE updateModifier* relationList SET assignmentList whereClause? dmlOrganization?
    ;

deleteStatement
    : ctes? DELETE deleteModifier* FROM multipartIdentifier tableAlias (USING relationList)? whereClause? dmlOrganization?   #deleteFrom
    | ctes? DELETE deleteModifier* multipartIdentifierList FROM relationList whereClause? dmlOrganization?                    #deleteAlias
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
    : (identifier | string) ((AT | AT_SIGN) (identifier | string))?
    ;

viewCheckOption
    : WITH (CASCADED | LOCAL)? CHECK OPTION
    ;

createIndexStatement
    : CREATE indexType? INDEX identifier ON multipartIdentifier LPAREN indexColumnList RPAREN
    ;

indexType
    : UNIQUE
    | FULLTEXT
    | SPATIAL
    ;

createDatabaseStatement
    : CREATE (DATABASE | SCHEMA) (IF NOT EXISTS)? identifier createDatabaseOption*
    ;

createDatabaseOption
    : DEFAULT? CHARACTER SET EQ? identifier
    | DEFAULT? CHARSET EQ? identifier
    | DEFAULT? COLLATE EQ? identifier
    | DEFAULT? ENCRYPTION EQ? string
    ;

dropDatabaseStatement
    : DROP (DATABASE | SCHEMA) (IF EXISTS)? identifier
    ;

dropIndexStatement
    : DROP INDEX identifier ON multipartIdentifier
    ;

dropTableStatement
    : DROP TEMPORARY? TABLE (IF EXISTS)? multipartIdentifierList
    ;

dropViewStatement
    : DROP VIEW (IF EXISTS)? multipartIdentifierList
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

truncateTableStatement
    : TRUNCATE TABLE? multipartIdentifier
    ;

renameTableStatement
    : RENAME TABLE renameTablePair (COMMA renameTablePair)*
    ;

renameTablePair
    : source=multipartIdentifier TO target=multipartIdentifier
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME (TO | AS)? multipartIdentifier   #alterTableRename
    | ALTER TABLE multipartIdentifier ADD COLUMN? identifier dataType         #alterTableAddColumn
    | ALTER TABLE multipartIdentifier CHANGE COLUMN? identifier identifier dataType columnPosition? #alterTableOther
    | ALTER TABLE multipartIdentifier MODIFY COLUMN? identifier dataType columnPosition?            #alterTableOther
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
    | DROP (KEY | INDEX) identifier
    | ADD PARTITION partitionDefinitionList
    | DROP PARTITION identifierList
    | TRUNCATE PARTITION identifierList
    | OPTIMIZE PARTITION identifierList
    | ANALYZE PARTITION identifierList
    | REPAIR PARTITION identifierList
    | EXCHANGE PARTITION identifier WITH TABLE multipartIdentifier identifier*
    | ADD PRIMARY KEY LPAREN identifierList RPAREN
    | ADD UNIQUE? (KEY | INDEX) identifier? LPAREN identifierList RPAREN
    | ADD CONSTRAINT identifier? FOREIGN KEY LPAREN identifierList RPAREN REFERENCES multipartIdentifier LPAREN identifierList RPAREN
    | SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

columnPosition
    : FIRST
    | AFTER identifier
    ;

analyzeTableStatement
    : ANALYZE TABLE multipartIdentifierList analyzeTableOption?
    ;

analyzeTableOption
    : UPDATE HISTOGRAM ON identifierList (WITH number BUCKETS)?
    | DROP HISTOGRAM ON identifierList
    ;

tableMaintenanceStatement
    : (CHECK | OPTIMIZE | REPAIR) TABLE multipartIdentifierList identifier*
    ;

explainStatement
    : EXPLAIN explainOption* ANALYZE? statement
    ;

explainOption
    : FORMAT EQ identifier
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
    | NAMES (identifier | DEFAULT)
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
    | BEGIN WORK?
    | COMMIT WORK? completionOption*
    | ROLLBACK WORK? completionOption*
    ;

doStatement
    : DO expressionList
    ;

transactionCharacteristicList
    : transactionCharacteristic (COMMA transactionCharacteristic)*
    ;

transactionCharacteristic
    : WITH CONSISTENT SNAPSHOT
    | READ (WRITE | ONLY)
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
      ON SCHEDULE (AT expression | EVERY expression identifier)
      DO eventBodyStatement
    ;

eventBodyStatement
    : insertStatement
    | replaceStatement
    | updateStatement
    | deleteStatement
    | .+?
    ;

accountStatement
    : CREATE USER .+?
    | ALTER USER .+?
    | DROP USER .+?
    | GRANT .+?
    | REVOKE .+?
    ;

adminStatement
    : FLUSH (TABLES | PRIVILEGES | STATUS | LOGS | .+?)
    | KILL (CONNECTION | QUERY)? number
    | RESET .+?
    ;

showStatement
    : SHOW FULL? TABLES ((FROM | IN) identifier)?
    | SHOW (DATABASES | SCHEMAS | VARIABLES | STATUS | WARNINGS | GRANTS | PROCESSLIST | ENGINES | COLLATION | COLLATIONS | CHARACTER SET)
    | SHOW showObject (FROM | IN) multipartIdentifier
    | SHOW .+? (TABLE | VIEW) multipartIdentifier
    | SHOW .+?
    ;

showObject
    : identifier
    | INDEX
    | KEY
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
    | CHECK LPAREN expression RPAREN
    | VISIBLE
    | INVISIBLE
    | GENERATED ALWAYS? AS LPAREN expression RPAREN (VIRTUAL | STORED)?
    | AS LPAREN expression RPAREN (VIRTUAL | STORED)?
    ;

tableConstraint
    : (CONSTRAINT identifier)? PRIMARY KEY LPAREN identifierList RPAREN
    | indexType? (KEY | INDEX) identifier? LPAREN indexColumnList RPAREN indexVisibility?
    | (CONSTRAINT identifier)? FOREIGN KEY LPAREN identifierList RPAREN REFERENCES multipartIdentifier LPAREN identifierList RPAREN
    | (CONSTRAINT identifier)? CHECK LPAREN expression RPAREN
    ;

indexColumnList
    : indexColumn (COMMA indexColumn)*
    ;

indexColumn
    : identifier (LPAREN NUMBER_LITERAL RPAREN)? (ASC | DESC)?
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
    | ROW_FORMAT EQ? identifier
    | PACK_KEYS EQ? (identifier | number)
    | STATS_PERSISTENT EQ? (identifier | number)
    | KEY_BLOCK_SIZE EQ? number
    | COMPRESSION EQ? string
    | TABLESPACE identifier
    | AUTOEXTEND_SIZE EQ? number
    | COMMENT EQ? string
    ;

partitionClause
    : PARTITION BY partitionMethod LPAREN identifierList RPAREN partitionCount? partitionDefinitionList?
    ;

partitionMethod
    : RANGE
    | HASH
    | KEY
    | LIST
    ;

partitionCount
    : PARTITIONS number
    ;

partitionDefinitionList
    : LPAREN partitionDefinition (COMMA partitionDefinition)* RPAREN
    ;

partitionDefinition
    : PARTITION identifier VALUES LESS THAN LPAREN literalValueList RPAREN tableOption*
    | PARTITION identifier VALUES LESS THAN MAXVALUE tableOption*
    ;

literalValueList
    : literalValue (COMMA literalValue)*
    ;

literalValue
    : number
    | string
    | identifier
    ;

dataType
    : identifier (LPAREN dataTypeArgument (COMMA dataTypeArgument)* RPAREN)?
    | identifier LT dataType (COMMA dataType)* GT
    ;

dataTypeArgument
    : NUMBER_LITERAL
    | string
    | identifier
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
    | nonReservedKeyword
    ;

strictIdentifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | nonReservedKeyword
    ;

nonReservedKeyword
    : ADD | AFTER | ALGORITHM | ANALYZE | ASC | AT | AUTO_INCREMENT | BINARY | CASCADED | CALL | CAST | CHANGE | CHARACTER | CHARSET | CHECK | COLLATE | COLLATION | COLLATIONS | CONNECTION
    | ALWAYS | COLUMN | COMMENT | CONSTRAINT | CURRENT | DATA | DATABASES | DEFAULT | DEFINER | DELAYED | DESCRIBE | DESC | DO | DUPLICATE | EACH | ENCLOSED | END | ENGINES
    | DATABASE | DEALLOCATE | DIV | DUMPFILE | ENCRYPTION | ENGINE | ESCAPED | EVENT | EVERY | EXCHANGE | EXECUTE | EXISTS | EXPLAIN | EXTERNAL | FALSE | FIELDS | FIRST | FLUSH | FOLLOWING | FOR | FORCE | FOREIGN | FORMAT | FULLTEXT | FUNCTION | GENERATED | GLOBAL | GRANT | GRANTS | HIGH_PRIORITY | IF | IGNORE | INDEX | INTERVAL | INVOKER | KEY | KILL | LATERAL | LIKE | LIMIT | LINES | LOCK | LOGS | MERGE | MOD | MODE | MODIFY | NAMES | NO | NULL
    | INFILE | KEY_BLOCK_SIZE | LOAD | LOCAL | LOW_PRIORITY | OFFSET | OPTIMIZE | OPTION | OUTFILE | OVER | PACK_KEYS | PARTITION | PARTITIONS | PRECEDING | PREPARE | PRIVILEGES | PROCEDURE | PROCESSLIST | QUERY | RANGE | READ | RECURSIVE | REFERENCES | REPAIR | REPLACE | RENAME | RESET | ROW | ROW_FORMAT | ROWS | SCHEDULE | SEPARATOR
    | BUCKETS | HASH | HISTOGRAM | LESS | LIST | MAXVALUE | NATURAL | ONLY | PRIMARY | QUICK | REGEXP | RELEASE | REVOKE | RLIKE | ROLLBACK | ROLLUP | SCHEMAS | SECURITY | SESSION | SET | SHARE | SHOW | SNAPSHOT | SPATIAL | SQL | SQL_BIG_RESULT | SQL_BUFFER_RESULT | SQL_CACHE | SQL_CALC_FOUND_ROWS
    | SQL_NO_CACHE | SQL_SMALL_RESULT | START | STARTING | STATS_PERSISTENT | STORED | STRAIGHT_JOIN | TABLE | TABLESPACE | TEMPORARY | TEMPTABLE | TERMINATED | THAN | TO | TRANSACTION | TRUE | TRUNCATE | UNBOUNDED | UNDEFINED | UNIQUE | VALUES | VIEW | VIRTUAL | VISIBLE | INVISIBLE | WORK
    | AUTOEXTEND_SIZE | COMPRESSION | SCHEMA | STATUS | TABLES | TRIGGER | UNLOCK | USE | USER | VARIABLES | WARNINGS | WINDOW | WITH | WRITE | BEGIN | CHAIN | COMMIT | CONSISTENT
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
