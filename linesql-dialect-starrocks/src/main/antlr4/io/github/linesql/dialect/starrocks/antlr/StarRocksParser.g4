parser grammar StarRocksParser;

options { tokenVocab = StarRocksLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | insertStatement                                                #insertStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropTableStatement                                             #dropTableStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
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
    : GROUP BY expression (COMMA expression)*
    ;

havingClause
    : HAVING expression
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
    : INSERT (INTO | OVERWRITE) TABLE? multipartIdentifier
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)*)
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

updateStatement
    : UPDATE multipartIdentifier tableAlias
      SET assignmentList
      (FROM relationList)?
      whereClause?
    ;

deleteStatement
    : DELETE FROM multipartIdentifier tableAlias
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
      keyDesc?
      commentClause?
      distributionDesc?
      propertiesClause?
      (AS query)?
    | CREATE TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

createViewStatement
    : CREATE MATERIALIZED? (OR REPLACE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifier
    ;

truncateTableStatement
    : TRUNCATE TABLE multipartIdentifier
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME multipartIdentifier     #alterTableRename
    | ALTER TABLE multipartIdentifier ADD COLUMN? identifier dataType  #alterTableAddColumn
    | ALTER TABLE multipartIdentifier alterTableAction               #alterTableOther
    ;

alterTableAction
    : DROP COLUMN identifier
    | SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

showStatement
    : SHOW .+?
    ;

describeStatement
    : (DESCRIBE | DESC) .+?
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
    : ADD | AGGREGATE | ASC | BUCKETS | CAST | COLUMN | COMMENT | DEFAULT
    | DESCRIBE | DESC | DISTRIBUTED | DUPLICATE | END | EXISTS | EXTERNAL | FALSE
    | FORMAT | HASH | IF | INTERVAL | KEY | LATERAL | LIKE | LIMIT | MATERIALIZED
    | MAX | MIN | NULL
    | OFFSET | OVER | OVERWRITE | PARTITION | PRIMARY | PROPERTIES
    | RANDOM | RENAME | REPLACE | ROW | SHOW | STORED | SUM | TABLE
    | TEMPORARY | TO | TRUE | TRUNCATE | UNIQUE | VALUES | VIEW
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
