parser grammar FlinkParser;

options { tokenVocab = FlinkLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | insertStatement                                                #insertStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | mergeStatement                                                 #mergeStmt
    | createTableStatement                                           #createTableStmt
    | createMaterializedTableStatement                               #createMaterializedTableStmt
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
    | useStatement                                                   #useStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | commentStatement                                               #commentStmt
    | explainStatement                                               #explainStmt
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
    : selectClause fromClause? whereClause? groupByClause? havingClause? windowClause?
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
    : multipartIdentifier matchRecognizeClause tableAlias            #matchRecognizeRelation
    | multipartIdentifier temporalClause tableAlias                  #temporalTableName
    | multipartIdentifier tableAlias                                 #tableName
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    | LATERAL TABLE LPAREN functionName LPAREN tableFunctionArgList? RPAREN RPAREN tableAlias #lateralTableFunction
    | TABLE LPAREN functionName LPAREN tableFunctionArgList RPAREN RPAREN tableAlias  #tableFunction
    | UNNEST LPAREN expression RPAREN tableAlias                      #unnestTableFunction
    ;

joinRelation
    : joinType? JOIN relationPrimary temporalClause? joinCriteria?
    ;

temporalClause
    : FOR SYSTEM_TIME AS OF expression
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

matchRecognizeClause
    : MATCH_RECOGNIZE LPAREN
      (PARTITION BY expressionList)?
      (ORDER BY sortItem (COMMA sortItem)*)?
      (MEASURES matchMeasure (COMMA matchMeasure)*)?
      ((ONE | ALL) (ROW | ROWS) PER MATCH)?
      (AFTER MATCH SKIP_KEYWORD PAST LAST ROW)?
      PATTERN LPAREN patternTerm+ RPAREN
      (DEFINE matchDefine (COMMA matchDefine)*)?
      RPAREN
    ;

matchMeasure
    : expression AS identifier
    ;

matchDefine
    : identifier AS expression
    ;

patternTerm
    : identifier (PLUS | STAR | QUESTION)?
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

queryOrganization
    : (ORDER BY sortItem (COMMA sortItem)*)?
      (LIMIT expression)?
      (OFFSET expression)?
      (FETCH (FIRST | NEXT) expression ROWS? ONLY)?
    ;

sortItem
    : expression (ASC | DESC)?
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
    | functionName LPAREN STAR RPAREN filterClause? overClause?      #functionCallStar
    | functionName LPAREN setQuantifier? expressionList RPAREN filterClause? overClause?  #functionCall
    | functionName LPAREN RPAREN filterClause? overClause?           #functionCallEmpty
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | primaryExpression DOT identifier                                #dereference
    | identifier                                                     #columnReference
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

overClause
    : OVER (windowSpec | identifier)
    ;

filterClause
    : FILTER LPAREN WHERE expression RPAREN
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
    | VALUES
    | DEFAULT
    | LAST
    ;

expressionList
    : expression (COMMA expression)*
    ;

// ============ DML Statements ============

insertStatement
    : EXECUTE? INSERT (INTO | OVERWRITE) multipartIdentifier
      (PARTITION partitionSpec)?
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)*)
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
    : TABLE multipartIdentifier                                      #tvfTableArg
    | DESCRIPTOR LPAREN identifier RPAREN                            #tvfDescriptorArg
    | expression                                                     #tvfExprArg
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
    : CREATE TEMPORARY? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      distributionClause?
      partitionedByClause?
      ((WITH LPAREN propertyList RPAREN)? (likeClause | AS query)?
      | likeClause (WITH LPAREN propertyList RPAREN)?)
    | CREATE (OR REPLACE)? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      distributionClause?
      partitionedByClause?
      (WITH LPAREN propertyList RPAREN)?
      AS query
    ;

createViewStatement
    : CREATE TEMPORARY? (OR REPLACE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      commentClause?
      AS query
    ;

createMaterializedTableStatement
    : CREATE (OR ALTER)? MATERIALIZED TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      partitionedByClause?
      (WITH LPAREN propertyList RPAREN)?
      (FRESHNESS EQ expression)?
      (REFRESH_MODE EQ identifier)?
      (AS query)?
    ;

createCatalogStatement
    : CREATE CATALOG (IF NOT EXISTS)? identifier WITH LPAREN propertyList RPAREN
    ;

createDatabaseStatement
    : CREATE DATABASE (IF NOT EXISTS)? multipartIdentifier commentClause? (WITH LPAREN propertyList RPAREN)?
    ;

createFunctionStatement
    : CREATE (TEMPORARY SYSTEM?)? FUNCTION (IF NOT EXISTS)? multipartIdentifier
      AS string (LANGUAGE functionLanguage)? functionUsingClause? (WITH LPAREN propertyList RPAREN)?
    ;

createModelStatement
    : CREATE TEMPORARY? MODEL (IF NOT EXISTS)? multipartIdentifier (WITH LPAREN propertyList RPAREN)?
    ;

dropTableStatement
    : DROP TEMPORARY? TABLE (IF EXISTS)? multipartIdentifier
    ;

dropViewStatement
    : DROP TEMPORARY? VIEW (IF EXISTS)? multipartIdentifier
    ;

dropDatabaseStatement
    : DROP DATABASE (IF EXISTS)? multipartIdentifier (RESTRICT | CASCADE)?
    ;

dropFunctionStatement
    : DROP (TEMPORARY SYSTEM?)? FUNCTION (IF EXISTS)? multipartIdentifier
    ;

dropCatalogStatement
    : DROP CATALOG (IF EXISTS)? identifier
    ;

dropModelStatement
    : DROP TEMPORARY? MODEL (IF EXISTS)? multipartIdentifier
    ;

dropMaterializedTableStatement
    : DROP MATERIALIZED TABLE (IF EXISTS)? multipartIdentifier
    ;

truncateTableStatement
    : TRUNCATE TABLE? multipartIdentifier
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME (TO | AS)? multipartIdentifier   #alterTableRename
    | ALTER TABLE multipartIdentifier RENAME identifier TO identifier         #alterTableRenameColumn
    | ALTER TABLE multipartIdentifier ADD COLUMN? tableElement columnPosition? #alterTableAddColumn
    | ALTER TABLE multipartIdentifier ADD LPAREN tableElementList RPAREN      #alterTableAddColumns
    | ALTER TABLE multipartIdentifier MODIFY COLUMN? tableElement columnPosition? #alterTableModifyColumn
    | ALTER TABLE multipartIdentifier MODIFY LPAREN tableElementList RPAREN   #alterTableModifyColumns
    | ALTER TABLE multipartIdentifier DROP COLUMN? identifier                 #alterTableDropColumn
    | ALTER TABLE multipartIdentifier DROP LPAREN identifierList RPAREN       #alterTableDropColumns
    | ALTER TABLE multipartIdentifier ADD PARTITION partitionSpec (WITH LPAREN propertyList RPAREN)? (PARTITION partitionSpec (WITH LPAREN propertyList RPAREN)?)* #alterTableAddPartition
    | ALTER TABLE multipartIdentifier DROP PARTITION partitionSpec (COMMA PARTITION partitionSpec)* #alterTableDropPartition
    | ALTER TABLE multipartIdentifier ADD distributionClause                  #alterTableAddDistribution
    | ALTER TABLE multipartIdentifier DROP DISTRIBUTION                       #alterTableDropDistribution
    | ALTER TABLE multipartIdentifier DROP PRIMARY KEY                        #alterTableDropPrimaryKey
    | ALTER TABLE multipartIdentifier DROP WATERMARK                          #alterTableDropWatermark
    | ALTER TABLE multipartIdentifier RESET LPAREN stringList RPAREN          #alterTableResetProperties
    | ALTER TABLE multipartIdentifier alterTableAction                        #alterTableOther
    ;

alterTableAction
    : SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

alterViewStatement
    : ALTER VIEW multipartIdentifier RENAME TO multipartIdentifier            #alterViewRename
    | ALTER VIEW multipartIdentifier AS query                                  #alterViewAsQuery
    ;

alterDatabaseStatement
    : ALTER DATABASE multipartIdentifier SET LPAREN propertyList RPAREN
    ;

alterFunctionStatement
    : ALTER (TEMPORARY SYSTEM?)? FUNCTION (IF EXISTS)? multipartIdentifier
      AS string (LANGUAGE functionLanguage)?
    ;

alterMaterializedTableStatement
    : ALTER MATERIALIZED TABLE multipartIdentifier materializedTableAction
    ;

materializedTableAction
    : SUSPEND
    | RESUME (WITH LPAREN propertyList RPAREN)?
    | REFRESH (PARTITION partitionSpec)?
    | SET LPAREN propertyList RPAREN
    | RESET LPAREN stringList RPAREN
    ;

useStatement
    : USE CATALOG identifier
    | USE (DATABASE)? multipartIdentifier
    ;

showStatement
    : SHOW .+? TABLE multipartIdentifier
    | SHOW .+?
    ;

describeStatement
    : (DESCRIBE | DESC) TABLE? multipartIdentifier
    | (DESCRIBE | DESC) .+?
    ;

commentStatement
    : COMMENT ON (TABLE multipartIdentifier | COLUMN multipartIdentifier)
      IS string
    ;

explainStatement
    : EXPLAIN (statement | .+?)
    ;

utilityStatement
    : LOAD MODULE identifier (WITH LPAREN propertyList RPAREN)?
    | UNLOAD MODULE identifier
    | USE MODULES identifierList
    | ADD JAR string
    | REMOVE JAR string
    | SET (string EQ string)?
    | RESET string?
    | SHOW (CURRENT (CATALOG | DATABASE) | CATALOGS | DATABASES | TABLES | VIEWS | FUNCTIONS | MODULES | JARS)
    | SHOW CREATE (TABLE | VIEW) multipartIdentifier
    ;

// ============ EXECUTE STATEMENT SET ============

executeStatementSet
    : EXECUTE STATEMENT SET BEGIN (insertStatement SEMI)+ END
    ;

// ============ DDL Helpers ============

tableElementList
    : tableElement (COMMA tableElement)*
    ;

tableElement
    : identifier dataType metadataClause? columnConstraint*           #columnDefinition
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
    : property (COMMA property)*
    ;

property
    : string EQ string
    ;

likeClause
    : LIKE source=multipartIdentifier (LPAREN likeOption (COMMA likeOption)* RPAREN)?
    ;

likeOption
    : (INCLUDING | EXCLUDING | OVERWRITING) identifier
    | (INCLUDING | EXCLUDING | OVERWRITING) OPTIONS
    ;

partitionedByClause
    : PARTITIONED BY LPAREN identifierList RPAREN
    ;

distributionClause
    : DISTRIBUTED BY (HASH LPAREN identifierList RPAREN | LPAREN identifierList RPAREN) (INTO number BUCKETS?)?
    | DISTRIBUTED INTO number BUCKETS?
    ;

partitionSpec
    : LPAREN partitionSpecItem (COMMA partitionSpecItem)* RPAREN
    ;

partitionSpecItem
    : identifier EQ expression
    ;

stringList
    : string (COMMA string)*
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
    : ADD | AFTER | ALL | ALWAYS | ANALYZE | ANY | ARTIFACT | ASC | BEGIN | BUCKET | BUCKETS | CASCADE
    | CATALOG | CATALOGS | CAST | COLUMN | COMMENT | CONNECTOR | CONSTRAINT | CONTINUOUS
    | CUMULATE | CUBE | CURRENT | DATABASE | DATABASES | DAY | DEFAULT | DEFINE | DESCRIBE | DESC | DESCRIPTOR | DISTRIBUTED
    | DISTRIBUTION | END | ENFORCED | EXCLUDING | EXISTS | EXECUTE | EXPLAIN
    | EXTERNAL | FALSE | FETCH | FILTER | FIRST | FOLLOWING | FOR | FORMAT | FRESHNESS | FUNCTION | FUNCTIONS
    | GENERATED | GROUPING | HASH | HOP | HOUR | IF | INCLUDING | INTERVAL | JAVA
    | JAR | JARS | KEY | LANGUAGE | LAST | LATERAL | LIKE | LIMIT | LOAD | MATCH | MATCHED
    | MATCH_RECOGNIZE | MATERIALIZED | MEASURES | MERGE | METADATA | MINUTE | MODEL | MODULE | MODULES | NEXT | NULL | OF | ONE | ONLY
    | OFFSET | OPTIONS | OVER | OVERWRITE | OVERWRITING | PARTITION | PARTITIONED
    | PAST | PATTERN | PER | PRECEDING | PERIOD | PRIMARY | PYTHON | RANGE | REFRESH | REFRESH_MODE | RENAME | REPLACE | RESET
    | REMOVE | RESUME | RESTRICT | ROLLUP | ROW | ROWS | SCALA | SECOND | SESSION | SET | SETS | SHOW | SKIP_KEYWORD | SOME | STATEMENT | STORED
    | SUSPEND | SYS | SYSTEM | TABLE | TABLES | TEMPORARY | TO | TRUE | TRUNCATE | TUMBLE
    | UNBOUNDED | UNKNOWN | UNLOAD | UNNEST | USE | VALUES | VIEW | VIEWS | VIRTUAL | WATERMARK | WINDOW
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
