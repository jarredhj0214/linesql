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
    | createViewStatement                                            #createViewStmt
    | dropTableStatement                                             #dropTableStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | commentStatement                                               #commentStmt
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
    : multipartIdentifier temporalClause tableAlias                  #temporalTableName
    | multipartIdentifier tableAlias                                 #tableName
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    | TABLE LPAREN functionName LPAREN tableFunctionArgList RPAREN RPAREN tableAlias  #tableFunction
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
    : LPAREN (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
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
    ;

expressionList
    : expression (COMMA expression)*
    ;

// ============ DML Statements ============

insertStatement
    : INSERT (INTO | OVERWRITE) multipartIdentifier
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
      (WITH LPAREN propertyList RPAREN)?
      (AS query)?
    | CREATE TEMPORARY? TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

createViewStatement
    : CREATE (OR REPLACE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifier
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

// ============ EXECUTE STATEMENT SET ============

executeStatementSet
    : EXECUTE STATEMENT SET BEGIN (insertStatement SEMI)+ END
    ;

// ============ DDL Helpers ============

tableElementList
    : tableElement (COMMA tableElement)*
    ;

tableElement
    : identifier dataType columnConstraint*                          #columnDefinition
    | WATERMARK FOR identifier AS expression                         #watermarkDefinition
    | PRIMARY KEY LPAREN identifierList RPAREN NOT ENFORCED          #primaryKeyDefinition
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
    : ADD | ASC | BEGIN | CAST | COLUMN | COMMENT | CONNECTOR | CUMULATE | CURRENT
    | DEFAULT | DESCRIBE | DESC | DESCRIPTOR | END | ENFORCED | EXISTS | EXECUTE
    | EXTERNAL | FALSE | FOLLOWING | FOR | FORMAT | HOP | IF | INTERVAL | KEY
    | LATERAL | LIKE | LIMIT | MATCHED | MERGE | NULL | OF | OFFSET | OVER
    | OVERWRITE | PARTITION | PRECEDING | PERIOD | PRIMARY | RANGE | RENAME
    | REPLACE | ROW | ROWS | SESSION | SET | SHOW | STATEMENT | STORED | TABLE
    | TEMPORARY | TO | TRUE | TRUNCATE | TUMBLE | UNBOUNDED | VALUES | VIEW
    | WATERMARK
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
