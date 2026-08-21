parser grammar StarRocksParser;

options { tokenVocab = StarRocksLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | adminStatement                                                 #adminStmt
    | insertStatement                                                #insertStmt
    | exportTableStatement                                           #exportTableStmt
    | loadLabelStatement                                             #loadLabelStmt
    | createRoutineLoadStatement                                     #createRoutineLoadStmt
    | routineLoadControlStatement                                    #routineLoadControlStmt
    | cancelLoadStatement                                            #cancelLoadStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | createIndexStatement                                           #createIndexStmt
    | createDatabaseStatement                                        #createDatabaseStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropIndexStatement                                             #dropIndexStmt
    | dropDatabaseStatement                                          #dropDatabaseStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | truncateTableStatement                                         #truncateTableStmt
    | refreshMaterializedViewStatement                               #refreshMaterializedViewStmt
    | alterTableStatement                                            #alterTableStmt
    | analyzeTableStatement                                          #analyzeTableStmt
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
    : multipartIdentifier tableAlias                                 #tableName
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    ;

joinRelation
    : joinType? JOIN relationPrimary joinCriteria?
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
      (LIMIT expression)?
      (OFFSET expression)?
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
    | functionName LPAREN STAR RPAREN (OVER windowSpec)?             #functionCallStar
    | functionName LPAREN setQuantifier? expressionList RPAREN (OVER windowSpec)?  #functionCall
    | functionName LPAREN RPAREN (OVER windowSpec)?                  #functionCallEmpty
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
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

windowSpec
    : LPAREN (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? RPAREN
    ;

functionName
    : identifier
    | LEFT
    | RIGHT
    | REPLACE
    | IF
    | CAST
    ;

expressionList
    : expression (COMMA expression)*
    ;

// ============ DML Statements ============

insertStatement
    : ctes? INSERT (INTO | OVERWRITE) TABLE? multipartIdentifier
      insertLabelClause?
      partitionClause?
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)*)
    ;

insertLabelClause
    : WITH LABEL identifier
    ;

exportTableStatement
    : EXPORT TABLE source=multipartIdentifier partitionClause? TO string propertiesClause?
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

createRoutineLoadStatement
    : CREATE ROUTINE LOAD job=multipartIdentifier ON target=multipartIdentifier (routineLoadClause COMMA?)*
    ;

routineLoadClause
    : COLUMNS TERMINATED BY string
    | COLUMNS LPAREN identifierList RPAREN
    | whereClause
    | propertiesClause
    | FROM identifier LPAREN propertyList RPAREN
    ;

routineLoadControlStatement
    : (STOP | PAUSE | RESUME) ROUTINE LOAD FOR? job=multipartIdentifier
    ;

cancelLoadStatement
    : CANCEL LOAD (FROM identifier)? WHERE expression
    ;

adminStatement
    : ADMIN SET FRONTEND CONFIG LPAREN propertyList RPAREN
    | ADMIN SET REPLICA STATUS propertiesClause
    ;

loadLabelStatement
    : LOAD LABEL label=multipartIdentifier LPAREN loadDataElement (COMMA loadDataElement)* RPAREN
      (WITH BROKER identifier? (LPAREN propertyList RPAREN)?)?
      propertiesClause?
    ;

loadDataElement
    : DATA INFILE LPAREN string (COMMA string)* RPAREN INTO TABLE target=multipartIdentifier loadDataOption*
    ;

loadDataOption
    : COLUMNS TERMINATED BY string
    | FORMAT AS string
    | LPAREN identifierList RPAREN
    | whereClause
    | SET LPAREN assignmentList RPAREN
    | PARTITION LPAREN identifierList RPAREN
    ;

updateStatement
    : ctes? UPDATE multipartIdentifier tableAlias
      SET assignmentList
      (FROM relationList)?
      whereClause?
    ;

deleteStatement
    : ctes? DELETE FROM multipartIdentifier tableAlias
      (USING relationList)?
      whereClause?
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : multipartIdentifier EQ expression
    ;

// ============ DDL Statements ============

createTableStatement
    : CREATE EXTERNAL? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      engineClause?
      keyDesc?
      commentClause?
      partitionDesc?
      distributionDesc?
      propertiesClause?
      (AS query)?
    | CREATE TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

engineClause
    : ENGINE EQ? identifier
    ;

createViewStatement
    : CREATE MATERIALIZED? (OR REPLACE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      materializedViewOption*
      AS query
    ;

materializedViewOption
    : distributionDesc
    | REFRESH (SYNC | ASYNC)?
    | propertiesClause
    ;

createIndexStatement
    : CREATE INDEX identifier ON multipartIdentifier LPAREN identifierList RPAREN
    ;

createDatabaseStatement
    : CREATE DATABASE (IF NOT EXISTS)? identifier propertiesClause?
    ;

dropIndexStatement
    : DROP INDEX identifier ON multipartIdentifier
    ;

dropDatabaseStatement
    : DROP DATABASE (IF EXISTS)? identifier
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifier
    ;

dropViewStatement
    : DROP MATERIALIZED? VIEW (IF EXISTS)? multipartIdentifier
    ;

truncateTableStatement
    : TRUNCATE TABLE multipartIdentifier
    ;

refreshMaterializedViewStatement
    : REFRESH MATERIALIZED VIEW multipartIdentifier refreshMaterializedViewOption*
    ;

refreshMaterializedViewOption
    : PARTITION identifier
    | WITH (SYNC | ASYNC) MODE
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME (TO | AS)? multipartIdentifier     #alterTableRename
    | ALTER TABLE multipartIdentifier SWAP WITH TABLE multipartIdentifier        #alterTableSwap
    | ALTER TABLE multipartIdentifier ADD COLUMN? identifier dataType  #alterTableAddColumn
    | ALTER TABLE multipartIdentifier alterTableAction               #alterTableOther
    ;

alterTableAction
    : DROP COLUMN identifier
    | ADD PARTITION identifier VALUES LESS THAN LPAREN expressionList RPAREN
    | DROP PARTITION identifier
    | SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

analyzeTableStatement
    : ANALYZE TABLE multipartIdentifier
    ;

showStatement
    : SHOW CREATE TABLE multipartIdentifier
    | SHOW CREATE MATERIALIZED? VIEW multipartIdentifier
    | SHOW PARTITIONS FROM multipartIdentifier
    | SHOW showObject (FROM | IN) multipartIdentifier
    | SHOW .+?
    ;

showObject
    : identifier
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
    : identifier dataType aggregateType? columnConstraint*
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
    ;

keyDesc
    : (DUPLICATE | AGGREGATE | UNIQUE | PRIMARY) KEY LPAREN identifierList RPAREN
    ;

commentClause
    : COMMENT string
    ;

distributionDesc
    : DISTRIBUTED BY HASH LPAREN identifierList RPAREN (BUCKETS number)?
    | DISTRIBUTED BY RANDOM (BUCKETS number)?
    ;

propertiesClause
    : PROPERTIES LPAREN propertyList RPAREN
    ;

partitionDesc
    : PARTITION BY RANGE LPAREN identifierList RPAREN
      LPAREN partitionDefinition (COMMA partitionDefinition)* RPAREN
    ;

partitionDefinition
    : PARTITION identifier VALUES LESS THAN LPAREN expressionList RPAREN
    ;

partitionClause
    : PARTITION LPAREN identifierList RPAREN
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
    : ADD | ADMIN | AGGREGATE | ANALYZE | ASC | BUCKETS | CANCEL | CAST | COLUMN | COLUMNS | COMMENT | CONFIG | DEFAULT
    | BROKER | DATA | DATABASE | DESCRIBE | DESC | DISTRIBUTED | DUPLICATE | END | ENGINE | EXISTS | EXPORT | EXTERNAL | FALSE
    | ASYNC | BITMAP_UNION | CUBE | FOR | FORMAT | FRONTEND | GROUPING | HASH | HLL_UNION | IF | INDEX | INFILE | INTERVAL | KEY | LABEL | LATERAL | LESS | LIKE | LIMIT | LOAD | MATERIALIZED
    | MAX | MIN | MODE | NULL | OLAP
    | OFFSET | OVER | OVERWRITE | PARTITION | PARTITIONS | PAUSE | PRIMARY | PROPERTIES | QUALIFY
    | RANDOM | RANGE | REFRESH | RENAME | REPLACE | REPLACE_IF_NOT_NULL | REPLICA | RESUME | ROLLUP | ROUTINE | ROW | SETS | SHOW | STATUS | STOP | STORED | SUM | SWAP | SYNC | TABLE
    | TEMPORARY | TERMINATED | THAN | TO | TRUE | TRUNCATE | UNIQUE | VALUES | VIEW
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
