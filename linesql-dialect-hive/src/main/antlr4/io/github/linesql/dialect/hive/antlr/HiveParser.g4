parser grammar HiveParser;

options { tokenVocab = HiveLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | insertDirectoryStatement                                       #insertDirectoryStmt
    | insertStatement                                                #insertStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropTableStatement                                             #dropTableStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
    | createDatabaseStatement                                        #createDatabaseStmt
    | dropDatabaseStatement                                          #dropDatabaseStmt
    | useStatement                                                   #useStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | commentStatement                                               #commentStmt
    | loadDataStatement                                              #loadDataStmt
    | repairTableStatement                                           #repairTableStmt
    | analyzeTableStatement                                          #analyzeTableStmt
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
    : selectClause fromClause? whereClause? groupByClause? havingClause?
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
    : relationPrimary lateralView* joinRelation*
    ;

lateralView
    : LATERAL VIEW functionName LPAREN expressionList RPAREN identifier AS identifier (COMMA identifier)*
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
    : GROUP BY expression (COMMA expression)*
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
    | DEFAULT                                                        #defaultLiteral
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
    | VALUES
    | DEFAULT
    ;

expressionList
    : expression (COMMA expression)*
    ;

// ============ DML Statements ============

insertStatement
    : INSERT (INTO | OVERWRITE) TABLE? multipartIdentifier
      (PARTITION partitionSpec)?
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
      commentClause?
      (PARTITIONED BY LPAREN tableElementList RPAREN)?
      (STORED AS identifier)?
      (ROW FORMAT identifier)?
      (LOCATION string)?
      (TBLPROPERTIES LPAREN propertyList RPAREN)?
      (AS query)?
    | CREATE TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

createViewStatement
    : CREATE (OR REPLACE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
    ;

createDatabaseStatement
    : CREATE (DATABASE | SCHEMA) (IF NOT EXISTS)? identifier databaseProperty*
    ;

databaseProperty
    : COMMENT string
    | LOCATION string
    | TBLPROPERTIES LPAREN propertyList RPAREN
    ;

dropDatabaseStatement
    : DROP (DATABASE | SCHEMA) (IF EXISTS)? identifier (CASCADE | RESTRICT)?
    ;

useStatement
    : USE identifier
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifier PURGE?
    ;

truncateTableStatement
    : TRUNCATE TABLE multipartIdentifier (PARTITION partitionSpec)?
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME (TO)? multipartIdentifier   #alterTableRename
    | ALTER TABLE multipartIdentifier ADD COLUMNS? identifier dataType   #alterTableAddColumn
    | ALTER TABLE multipartIdentifier alterTableAction                   #alterTableOther
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
    : (DESCRIBE | DESC) TABLE multipartIdentifier
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

repairTableStatement
    : MSCK? REPAIR TABLE multipartIdentifier (SYNC? PARTITIONS?)?
    ;

analyzeTableStatement
    : ANALYZE TABLE multipartIdentifier (PARTITION partitionSpec)? COMPUTE STATISTICS NOSCAN?
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

tableElement
    : identifier dataType columnConstraint*
    ;

columnConstraint
    : NOT NULL
    | NULL
    | COMMENT string
    | DEFAULT expression
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
    : ADD | ANALYZE | ASC | CASCADE | CAST | CLUSTER | COLLECTION | COLUMN | COLUMNS | COMMENT | COMPUTE | DATA | DATABASE | DATABASES | DEFAULT
    | DELIMITED | DESCRIBE | DESC | DIRECTORY | DISTRIBUTE | END | EXISTS | EXTERNAL | FALSE | FIELDS | FORMAT
    | IF | INPUTFORMAT | INTERVAL | ITEMS | KEYS | LATERAL | LIKE | LIMIT | LINES | LOAD | LOCAL | LOCATION | MAP | NULL
    | MSCK | NOSCAN | OFFSET | OVER | OVERWRITE | PARTITION | PARTITIONED | PARTITIONS | PURGE | RENAME
    | OUTPUTFORMAT | REPAIR | REPLACE | RESTRICT | ROW | SCHEMA | SCHEMAS | SERDEPROPERTIES | SET | SHOW | SORT | STATISTICS | STORED | SYNC | TABLE | TBLPROPERTIES
    | TEMPORARY | TERMINATED | TO | TRUE | TRUNCATE | USE | VALUES | VIEW
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
