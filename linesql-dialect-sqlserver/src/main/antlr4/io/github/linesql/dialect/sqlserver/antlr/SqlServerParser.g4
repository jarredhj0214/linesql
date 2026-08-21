parser grammar SqlServerParser;

options { tokenVocab = SqlServerLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | insertStatement                                                #insertStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | mergeStatement                                                 #mergeStmt
    | createIndexStatement                                           #createIndexStmt
    | alterIndexStatement                                            #alterIndexStmt
    | dropIndexStatement                                             #dropIndexStmt
    | createSchemaStatement                                          #createSchemaStmt
    | createProcedureStatement                                       #createProcedureStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropSchemaStatement                                            #dropSchemaStmt
    | dropProcedureStatement                                         #dropProcedureStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
    | useStatement                                                   #useStmt
    | setStatement                                                   #setStmt
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
    : selectClause selectIntoClause? fromClause? whereClause? groupByClause? havingClause?
    ;

selectClause
    : SELECT setQuantifier? topClause? selectItemList
    ;

selectIntoClause
    : INTO multipartIdentifier
    ;

topClause
    : TOP (LPAREN expression RPAREN | NUMBER_LITERAL)
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
    : multipartIdentifier tableHint? tableAlias                      #tableName
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    ;

tableHint
    : WITH LPAREN identifier (COMMA identifier)* RPAREN
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
    | CAST
    | VALUES
    | DEFAULT
    ;

expressionList
    : expression (COMMA expression)*
    ;

// ============ DML Statements ============

insertStatement
    : ctes? INSERT INTO? TABLE? multipartIdentifier
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)*)
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

updateStatement
    : ctes? UPDATE multipartIdentifier tableAlias SET assignmentList fromClause? whereClause?
    ;

deleteStatement
    : ctes? DELETE FROM multipartIdentifier tableAlias whereClause?             #deleteSimple
    | ctes? DELETE multipartIdentifier FROM relationList whereClause?           #deleteFromJoin
    ;

mergeStatement
    : MERGE INTO? multipartIdentifier tableAlias
      USING (multipartIdentifier tableAlias | LPAREN query RPAREN tableAlias)
      ON expression
      mergeClause+
    ;

mergeClause
    : WHEN MATCHED THEN mergeMatchedAction
    | WHEN NOT MATCHED (BY identifier)? THEN mergeNotMatchedAction
    ;

mergeMatchedAction
    : UPDATE SET assignmentList whereClause?
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
    : CREATE UNIQUE? (CLUSTERED | NONCLUSTERED)? INDEX multipartIdentifier
      ON multipartIdentifier LPAREN indexElementList RPAREN
      (INCLUDE LPAREN identifierList RPAREN)?
      whereClause?
    ;

dropIndexStatement
    : DROP INDEX multipartIdentifier ON multipartIdentifier
    ;

alterIndexStatement
    : ALTER INDEX (indexName=multipartIdentifier | ALL)
      ON tableName=multipartIdentifier
      alterIndexAction
      alterIndexOption*
    ;

alterIndexAction
    : REBUILD
    | REORGANIZE
    | DISABLE
    ;

alterIndexOption
    : identifier
    | LPAREN .+? RPAREN
    | EQ
    | number
    | string
    | COMMA
    ;

indexElementList
    : indexElement (COMMA indexElement)*
    ;

indexElement
    : expression (ASC | DESC)?
    ;

createTableStatement
    : CREATE TEMPORARY? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      (AS query)?
    | CREATE TEMPORARY? TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

createSchemaStatement
    : CREATE SCHEMA identifier
    ;

createProcedureStatement
    : CREATE (OR ALTER)? PROCEDURE multipartIdentifier .+?
    ;

createViewStatement
    : CREATE (OR (REPLACE | ALTER))? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifier
    ;

dropViewStatement
    : DROP VIEW (IF EXISTS)? multipartIdentifier
    ;

dropSchemaStatement
    : DROP SCHEMA (IF EXISTS)? identifier
    ;

dropProcedureStatement
    : DROP PROCEDURE (IF EXISTS)? multipartIdentifier
    ;

truncateTableStatement
    : TRUNCATE TABLE? multipartIdentifier
    ;

alterTableStatement
    : ALTER TABLE multipartIdentifier RENAME (TO | AS)? multipartIdentifier   #alterTableRename
    | ALTER TABLE multipartIdentifier ADD COLUMN? identifier dataType         #alterTableAddColumn
    | ALTER TABLE multipartIdentifier alterTableAction                        #alterTableOther
    ;

alterTableAction
    : DROP COLUMN identifier
    | SET LPAREN propertyList RPAREN
    | COMMENT EQ? string
    | .+?
    ;

useStatement
    : USE identifier
    ;

setStatement
    : SET identifier (ON | OFF | EQ expression)?
    ;

showStatement
    : SHOW .+? TABLE multipartIdentifier
    | SHOW .+?
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
    : ADD | ASC | CAST | COLUMN | COMMENT | DEFAULT
    | CLUSTERED | DESCRIBE | DESC | DISABLE | END | EXISTS | EXTERNAL | FALSE
    | INCLUDE | INDEX | INTERVAL | KEY | LIKE | LIMIT | NOLOCK | NONCLUSTERED | NULL | OFF
    | OFFSET | OUTPUT | OVER | PARTITION | REPLACE | RENAME
    | PRIMARY | PROCEDURE | REBUILD | REORGANIZE | SCHEMA | SET | SHOW | TABLE | TEMPORARY | TO | TOP | TRUE | TRUNCATE | USE | VALUES | VIEW
    | UNIQUE
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
