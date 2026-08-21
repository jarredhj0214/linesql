parser grammar PostgreSqlParser;

options { tokenVocab = PostgreSqlLineageLexer; }

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
    | createSchemaStatement                                          #createSchemaStmt
    | createFunctionStatement                                        #createFunctionStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropSchemaStatement                                            #dropSchemaStmt
    | dropFunctionStatement                                          #dropFunctionStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | refreshMaterializedViewStatement                               #refreshMaterializedViewStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | commentStatement                                               #commentStmt
    | copyStatement                                                  #copyStmt
    | vacuumStatement                                                #vacuumStmt
    | analyzeStatement                                               #analyzeStmt
    | reindexStatement                                               #reindexStmt
    | setStatement                                                   #setStmt
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
    | NOT? ILIKE valueExpression
    | IS NOT? NULL
    ;

valueExpression
    : primaryExpression                                              #valueExpressionDefault
    | operator=(MINUS | PLUS) valueExpression                        #unaryExpression
    | left=valueExpression operator=(STAR | SLASH | PERCENT) right=valueExpression   #arithmeticBinary
    | left=valueExpression operator=(PLUS | MINUS) right=valueExpression             #arithmeticBinaryPlusMinus
    | left=valueExpression CONCAT right=valueExpression              #concatExpression
    | valueExpression COLON COLON dataType                            #postgresCast
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
    | functionName LPAREN setQuantifier? expressionList functionSeparator? RPAREN (OVER windowSpec)?  #functionCall
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

functionSeparator
    : SEPARATOR expression
    ;

// ============ DML Statements ============

insertStatement
    : ctes? INSERT INTO? TABLE? multipartIdentifier
      (LPAREN columnList=identifierList RPAREN)?
      (query | VALUES valuesClause (COMMA valuesClause)* | SET assignmentList)
      onConflictClause?
      returningClause?
    ;

onConflictClause
    : ON CONFLICT (LPAREN identifierList RPAREN)? DO (NOTHING | UPDATE SET assignmentList)
    ;

returningClause
    : RETURNING (STAR | expression (COMMA expression)*)
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

updateStatement
    : ctes? UPDATE relation SET assignmentList fromClause? whereClause? returningClause?
    ;

deleteStatement
    : ctes? DELETE FROM multipartIdentifier tableAlias (USING relationList)? whereClause? returningClause?   #deleteFrom
    | ctes? DELETE multipartIdentifier FROM relationList whereClause?                        #deleteAlias
    ;

mergeStatement
    : ctes? MERGE INTO? multipartIdentifier tableAlias
      USING (multipartIdentifier tableAlias | LPAREN query RPAREN tableAlias)
      ON expression
      mergeClause+
      returningClause?
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
    | DO NOTHING
    ;

mergeNotMatchedAction
    : INSERT (LPAREN identifierList RPAREN)? VALUES LPAREN expressionList RPAREN
    | DO NOTHING
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : multipartIdentifier EQ expression
    ;

// ============ DDL Statements ============

createIndexStatement
    : CREATE UNIQUE? INDEX CONCURRENTLY? (IF NOT EXISTS)? multipartIdentifier
      ON multipartIdentifier (USING identifier)?
      LPAREN indexElementList RPAREN
      (WHERE expression)?
    ;

createSchemaStatement
    : CREATE SCHEMA (IF NOT EXISTS)? identifier
    ;

createFunctionStatement
    : CREATE (OR REPLACE)? (FUNCTION | PROCEDURE) multipartIdentifier LPAREN functionArgumentList? RPAREN
      (RETURNS dataType)?
      LANGUAGE identifier
      AS string
    ;

functionArgumentList
    : functionArgument (COMMA functionArgument)*
    ;

functionArgument
    : identifier? dataType
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
      tableOption*
      (AS query)?
    | CREATE TEMPORARY? TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

createViewStatement
    : CREATE (OR REPLACE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
    | CREATE MATERIALIZED VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifierList
    ;

dropViewStatement
    : DROP MATERIALIZED? VIEW (IF EXISTS)? multipartIdentifierList
    ;

dropSchemaStatement
    : DROP SCHEMA (IF EXISTS)? identifier
    ;

dropFunctionStatement
    : DROP (FUNCTION | PROCEDURE) (IF EXISTS)? multipartIdentifier (LPAREN dataTypeList? RPAREN)?
    ;

refreshMaterializedViewStatement
    : REFRESH MATERIALIZED VIEW CONCURRENTLY? multipartIdentifier
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
    : (DESCRIBE | DESC) .+?
    ;

commentStatement
    : COMMENT ON (TABLE multipartIdentifier | COLUMN multipartIdentifier)
      IS string
    ;

copyStatement
    : COPY multipartIdentifier (LPAREN columnList=identifierList RPAREN)?
      FROM copySource copyOptions?                                  #copyFromTable
    | COPY multipartIdentifier (LPAREN columnList=identifierList RPAREN)?
      TO copyTarget copyOptions?                                    #copyToTable
    | COPY LPAREN query RPAREN TO copyTarget copyOptions?           #copyToQuery
    ;

copySource
    : string
    | STDIN
    | PROGRAM string
    ;

copyTarget
    : string
    | STDOUT
    | PROGRAM string
    ;

copyOptions
    : WITH? LPAREN copyOption (COMMA copyOption)* RPAREN
    | WITH? copyOption (COMMA copyOption)*
    ;

copyOption
    : identifier (EQ? copyOptionValue)?
    ;

copyOptionValue
    : identifier
    | string
    | number
    | TRUE
    | FALSE
    ;

vacuumStatement
    : VACUUM vacuumOptionList? multipartIdentifier? (LPAREN identifierList RPAREN)?
    ;

vacuumOptionList
    : LPAREN vacuumOption (COMMA vacuumOption)* RPAREN
    | vacuumOption+
    ;

vacuumOption
    : FULL
    | FREEZE
    | VERBOSE
    | ANALYZE
    | identifier
    ;

analyzeStatement
    : ANALYZE VERBOSE? multipartIdentifier? (LPAREN identifierList RPAREN)?
    ;

reindexStatement
    : REINDEX (TABLE | INDEX) CONCURRENTLY? multipartIdentifier
    ;

setStatement
    : SET (SEARCH_PATH | identifier) (TO | EQ) expressionList
    ;

// ============ DDL Helpers ============

tableElementList
    : tableElement (COMMA tableElement)*
    ;

tableElement
    : LIKE multipartIdentifier likeOption*
    | identifier dataType columnConstraint*
    | tableConstraint
    ;

likeOption
    : (INCLUDING | EXCLUDING) (identifier | ALL)
    ;

columnConstraint
    : NOT NULL
    | NULL
    | COMMENT string
    | DEFAULT expression
    | AUTO_INCREMENT
    | PRIMARY KEY
    | UNIQUE KEY?
    ;

tableConstraint
    : (CONSTRAINT identifier)? PRIMARY KEY LPAREN identifierList RPAREN
    | UNIQUE (KEY | INDEX)? identifier? LPAREN identifierList RPAREN
    | (KEY | INDEX) identifier? LPAREN identifierList RPAREN
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

tableOption
    : ENGINE EQ? identifier
    | DEFAULT? CHARSET EQ? identifier
    | DEFAULT? CHARACTER SET EQ? identifier
    | COLLATE EQ? identifier
    | AUTO_INCREMENT EQ? number
    | COMMENT EQ? string
    ;

dataType
    : identifier (LPAREN NUMBER_LITERAL (COMMA NUMBER_LITERAL)* RPAREN)?
    | identifier LT dataType (COMMA dataType)* GT
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

multipartIdentifierList
    : multipartIdentifier (COMMA multipartIdentifier)*
    ;

identifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | DOUBLE_QUOTED_STRING
    | nonReservedKeyword
    ;

strictIdentifier
    : IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | DOUBLE_QUOTED_STRING
    | nonReservedKeyword
    ;

nonReservedKeyword
    : ADD | ASC | AUTO_INCREMENT | CAST | CHARSET | CHARACTER | COLLATE | CONCURRENTLY
    | ANALYZE | COLUMN | COMMENT | CONSTRAINT | COPY | CSV | DEFAULT | DELIMITER | DESCRIBE | DESC | END
    | ENGINE | EXCLUDING | EXISTS | EXTERNAL | FALSE | FORMAT | FUNCTION | HEADER | IF | ILIKE | INCLUDING | INDEX | INTERVAL | KEY | LANGUAGE | LATERAL | LIMIT | MATCHED | MATERIALIZED | NULL
    | OFFSET | OVER | PARTITION | REPLACE | RENAME | SEPARATOR
    | FREEZE | PRIMARY | PROCEDURE | PROGRAM | REFRESH | REINDEX | RETURNS | SCHEMA | SEARCH_PATH | SET | SHOW | STDIN | STDOUT | TABLE | TEMPORARY | TO | TRUE | TRUNCATE | UNIQUE | VACUUM | VALUES | VERBOSE | VIEW
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
