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
    | createExtensionStatement                                       #createExtensionStmt
    | createSchemaStatement                                          #createSchemaStmt
    | createFunctionStatement                                        #createFunctionStmt
    | schemaObjectControlStatement                                   #schemaObjectControlStmt
    | policyStatement                                                #policyStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropSchemaStatement                                            #dropSchemaStmt
    | dropExtensionStatement                                         #dropExtensionStmt
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
    | grantStatement                                                 #grantStmt
    | revokeStatement                                                #revokeStmt
    | setStatement                                                   #setStmt
    ;

// ============ Query ============

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
    | VALUES valuesClause (COMMA valuesClause)*                      #valuesQuery
    | LPAREN query RPAREN                                            #subqueryPrimary
    ;

querySpecification
    : selectClause selectIntoClause? fromClause? whereClause? groupByClause? havingClause?
    ;

selectClause
    : SELECT selectModifier? selectItemList
    ;

selectModifier
    : setQuantifier
    | DISTINCT ON LPAREN expressionList RPAREN
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

selectIntoClause
    : INTO (TEMPORARY | UNLOGGED)? TABLE? multipartIdentifier
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
    : ONLY? multipartIdentifier tableAlias                           #tableName
    | LATERAL? functionName LPAREN expressionList? RPAREN tableAlias #tableFunction
    | LPAREN VALUES valuesClause (COMMA valuesClause)* RPAREN tableAlias #valuesTable
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
    : (AS? strictIdentifier (LPAREN identifierList RPAREN)?)?
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
    : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
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
    | left=valueExpression operator=(ARROW | ARROW_TEXT | HASH_ARROW | HASH_ARROW_TEXT | CONTAINS | CONTAINED_BY | QUESTION | QUESTION_PIPE | QUESTION_AMP) right=valueExpression #postgresOperatorExpression
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
    | functionName LPAREN STAR RPAREN aggregateSuffix?               #functionCallStar
    | functionName LPAREN setQuantifier? expressionList functionSeparator? RPAREN aggregateSuffix?  #functionCall
    | functionName LPAREN RPAREN aggregateSuffix?                    #functionCallEmpty
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | primaryExpression LBRACKET expression? (COLON expression?)? RBRACKET #subscriptExpression
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

aggregateSuffix
    : withinGroupClause? filterClause? (OVER windowSpec)?
    ;

withinGroupClause
    : WITHIN GROUP LPAREN ORDER BY sortItem (COMMA sortItem)* RPAREN
    ;

filterClause
    : FILTER LPAREN WHERE expression RPAREN
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

windowSpec
    : LPAREN (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
    ;

windowFrame
    : (ROWS | RANGE | GROUPS) windowFrameExtent
    ;

windowFrameExtent
    : windowFrameBound
    | BETWEEN windowFrameBound AND windowFrameBound
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

functionSeparator
    : SEPARATOR expression
    ;

// ============ DML Statements ============

insertStatement
    : ctes? INSERT INTO? TABLE? multipartIdentifier
      (LPAREN columnList=identifierList RPAREN)?
      (OVERRIDING (SYSTEM | USER) VALUE)?
      (query | VALUES valuesClause (COMMA valuesClause)* | DEFAULT VALUES | SET assignmentList)
      onConflictClause?
      returningClause?
    ;

onConflictClause
    : ON CONFLICT conflictTarget? conflictWhereClause? DO (NOTHING | UPDATE SET assignmentList conflictWhereClause?)
    ;

conflictTarget
    : LPAREN identifierList RPAREN
    | ON CONSTRAINT identifier
    ;

conflictWhereClause
    : WHERE expression
    ;

returningClause
    : RETURNING (STAR | expression (COMMA expression)*)
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

updateStatement
    : ctes? UPDATE ONLY? relation SET assignmentList fromClause? whereClause? returningClause?
    ;

deleteStatement
    : ctes? DELETE FROM ONLY? multipartIdentifier tableAlias (USING relationList)? whereClause? returningClause?   #deleteFrom
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
      (INCLUDE LPAREN includeColumns=identifierList RPAREN)?
      (WHERE expression)?
    ;

createExtensionStatement
    : CREATE EXTENSION (IF NOT EXISTS)? identifier (WITH? extensionOptionList)?
    ;

extensionOptionList
    : extensionOption+
    ;

extensionOption
    : identifier identifier?
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

schemaObjectControlStatement
    : CREATE (TEMPORARY | UNLOGGED)? SEQUENCE (IF NOT EXISTS)? multipartIdentifier .+?
    | ALTER SEQUENCE (IF EXISTS)? multipartIdentifier .+?
    | DROP SEQUENCE (IF EXISTS)? multipartIdentifier (CASCADE | RESTRICT)?
    ;

policyStatement
    : CREATE POLICY identifier ON multipartIdentifier
      (AS (PERMISSIVE | RESTRICTIVE))?
      (FOR policyCommand)?
      (TO identifierList)?
      policyPredicate*
    | ALTER POLICY identifier ON multipartIdentifier
      (TO identifierList)?
      policyPredicate*
    | DROP POLICY (IF EXISTS)? identifier ON multipartIdentifier (CASCADE | RESTRICT)?
    ;

policyCommand
    : ALL
    | SELECT
    | INSERT
    | UPDATE
    | DELETE
    ;

policyPredicate
    : USING LPAREN expression RPAREN
    | WITH CHECK LPAREN expression RPAREN
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
    : CREATE (TEMPORARY | UNLOGGED)? TABLE (IF NOT EXISTS)? target=multipartIdentifier PARTITION OF partitionParent=multipartIdentifier partitionBound?
    | CREATE (TEMPORARY | UNLOGGED)? TABLE (IF NOT EXISTS)? multipartIdentifier
      LPAREN ctasColumnList=identifierList RPAREN
      tableOption*
      AS query withDataClause?
    | CREATE (TEMPORARY | UNLOGGED)? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      inheritsClause?
      partitionByClause?
      commentClause?
      tableOption*
      (AS query withDataClause?)?
    | CREATE (TEMPORARY | UNLOGGED)? TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

inheritsClause
    : INHERITS LPAREN multipartIdentifierList RPAREN
    ;

partitionByClause
    : PARTITION BY identifier LPAREN expressionList RPAREN
    ;

partitionBound
    : FOR VALUES partitionBoundKind
    ;

partitionBoundKind
    : FROM LPAREN expressionList RPAREN TO LPAREN expressionList RPAREN
    | IN LPAREN expressionList RPAREN
    | WITH LPAREN expressionList RPAREN
    ;

createViewStatement
    : CREATE (OR REPLACE)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query
    | CREATE MATERIALIZED VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      AS query withDataClause?
    ;

withDataClause
    : WITH NO? DATA
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifierList (CASCADE | RESTRICT)?
    ;

dropViewStatement
    : DROP MATERIALIZED? VIEW (IF EXISTS)? multipartIdentifierList (CASCADE | RESTRICT)?
    ;

dropSchemaStatement
    : DROP SCHEMA (IF EXISTS)? identifier (CASCADE | RESTRICT)?
    ;

dropExtensionStatement
    : DROP EXTENSION (IF EXISTS)? identifierList (CASCADE | RESTRICT)?
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
    : ALTER TABLE parent=multipartIdentifier ATTACH PARTITION child=multipartIdentifier partitionBound?  #alterTableAttachPartition
    | ALTER TABLE parent=multipartIdentifier DETACH PARTITION child=multipartIdentifier                  #alterTableDetachPartition
    | ALTER TABLE multipartIdentifier RENAME (TO | AS)? multipartIdentifier   #alterTableRename
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

grantStatement
    : GRANT .+? ON TABLE? multipartIdentifier TO .+?
    ;

revokeStatement
    : REVOKE .+? ON TABLE? multipartIdentifier FROM .+?
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
    : ADD | ASC | AUTO_INCREMENT | CAST | CASCADE | CHARSET | CHARACTER | COLLATE | CONCURRENTLY
    | ANALYZE | COLUMN | COMMENT | CONSTRAINT | COPY | CSV | DEFAULT | DELIMITER | DESCRIBE | DESC | END
    | ATTACH | CHECK | DETACH | ENGINE | EXCLUDING | EXISTS | EXTENSION | EXTERNAL | FALSE | FILTER | FIRST | FOLLOWING | FOR | FORMAT | FUNCTION | GRANT | GROUPS | HEADER | IF | ILIKE | INCLUDE | INCLUDING | INDEX | INHERITS | INTERVAL | KEY | LANGUAGE | LAST | LATERAL | LIMIT | MATCHED | MATERIALIZED | NULL | NULLS
    | OF | OFFSET | ONLY | OVERRIDING | OVER | PARTITION | PERMISSIVE | POLICY | PRECEDING | RANGE | RECURSIVE | REPLACE | RENAME | RESTRICT | RESTRICTIVE | REVOKE | ROW | ROWS | SEPARATOR
    | FREEZE | PRIMARY | PROCEDURE | PROGRAM | REFRESH | REINDEX | RETURNS | SCHEMA | SEARCH_PATH | SEQUENCE | SET | SHOW | STDIN | STDOUT | SYSTEM | TABLE | TEMPORARY | TO | TRUE | TRUNCATE | UNBOUNDED | UNLOGGED | UNIQUE | USER | VACUUM | VALUE | VALUES | VERBOSE | VIEW | WITHIN
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
