parser grammar PostgreSqlParser;

options { tokenVocab = PostgreSqlLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : transactionStatement                                           #transactionStmt
    | explainStatement                                               #explainStmt
    | query                                                          #statementDefault
    | insertStatement                                                #insertStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | mergeStatement                                                 #mergeStmt
    | createIndexStatement                                           #createIndexStmt
    | alterIndexStatement                                            #alterIndexStmt
    | dropIndexStatement                                             #dropIndexStmt
    | statisticsStatement                                            #statisticsStmt
    | typeStatement                                                  #typeStmt
    | domainStatement                                                #domainStmt
    | createExtensionStatement                                       #createExtensionStmt
    | createSchemaStatement                                          #createSchemaStmt
    | databaseStatement                                              #databaseStmt
    | createFunctionStatement                                        #createFunctionStmt
    | triggerStatement                                               #triggerStmt
    | ruleStatement                                                  #ruleStmt
    | schemaObjectControlStatement                                   #schemaObjectControlStmt
    | policyStatement                                                #policyStmt
    | publicationStatement                                           #publicationStmt
    | subscriptionStatement                                          #subscriptionStmt
    | foreignDataStatement                                           #foreignDataStmt
    | createTableStatement                                           #createTableStmt
    | createViewStatement                                            #createViewStmt
    | dropSchemaStatement                                            #dropSchemaStmt
    | dropExtensionStatement                                         #dropExtensionStmt
    | dropFunctionStatement                                          #dropFunctionStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | alterViewStatement                                             #alterViewStmt
    | alterMaterializedViewStatement                                 #alterMaterializedViewStmt
    | refreshMaterializedViewStatement                               #refreshMaterializedViewStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
    | showStatement                                                  #showStmt
    | describeStatement                                              #describeStmt
    | securityLabelStatement                                         #securityLabelStmt
    | commentStatement                                               #commentStmt
    | copyStatement                                                  #copyStmt
    | vacuumStatement                                                #vacuumStmt
    | analyzeStatement                                               #analyzeStmt
    | reindexStatement                                               #reindexStmt
    | clusterStatement                                               #clusterStmt
    | lockStatement                                                  #lockStmt
    | routineControlStatement                                        #routineControlStmt
    | principalStatement                                             #principalStmt
    | defaultPrivilegesStatement                                     #defaultPrivilegesStmt
    | grantStatement                                                 #grantStmt
    | revokeStatement                                                #revokeStmt
    | roleGrantStatement                                             #roleGrantStmt
    | setStatement                                                   #setStmt
    | resetStatement                                                 #resetStmt
    | discardStatement                                               #discardStmt
    ;

// ============ Query ============

query
    : ctes? queryTerm queryOrganization
    ;

ctes
    : WITH RECURSIVE? namedQuery (COMMA namedQuery)*
    ;

namedQuery
    : name=identifier (LPAREN columnAliases=identifierList RPAREN)? AS (NOT? MATERIALIZED)? LPAREN cteBody RPAREN
      searchClause? cycleClause?
    ;

searchClause
    : SEARCH (BREADTH | DEPTH) FIRST BY identifierList SET sequenceColumn=identifier
    ;

cycleClause
    : CYCLE identifierList SET markColumn=identifier (TO expression DEFAULT expression)?
      USING pathColumn=identifier
    ;

cteBody
    : query
    | insertStatement
    | updateStatement
    | deleteStatement
    | mergeStatement
    ;

queryTerm
    : queryPrimary                                                   #queryTermDefault
    | left=queryTerm INTERSECT setQuantifier? right=queryTerm        #setOperation
    | left=queryTerm (UNION | EXCEPT) setQuantifier? right=queryTerm #setOperation
    ;

queryPrimary
    : querySpecification                                             #queryPrimaryDefault
    | VALUES valuesClause (COMMA valuesClause)*                      #valuesQuery
    | TABLE inheritanceRelation                                     #tableQuery
    | LPAREN query RPAREN                                            #subqueryPrimary
    ;

querySpecification
    : selectClause selectIntoClause? fromClause? whereClause? groupByClause? havingClause? windowClause?
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
    : INTO (TEMPORARY | TEMP | UNLOGGED)? TABLE? multipartIdentifier
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

inheritanceRelation
    : multipartIdentifier STAR?
    | ONLY (multipartIdentifier | LPAREN multipartIdentifier RPAREN)
    ;

relationPrimary
    : inheritanceRelation (tableSampleClause tableAlias | tableAlias tableSampleClause?) #tableName
    | LATERAL? JSON_TABLE LPAREN expression COMMA expression (AS identifier)?
      jsonPassingClause? jsonTableColumns ((ERROR | EMPTY ARRAY?) ON ERROR)? RPAREN tableAlias #jsonTableRelation
    | LATERAL? XMLTABLE LPAREN (xmlNamespaces COMMA)? rowPath=expression
      PASSING xmlPassingMode? document=expression xmlPassingMode?
      COLUMNS xmlTableColumn (COMMA xmlTableColumn)* RPAREN tableAlias #xmlTableRelation
    | LATERAL? functionName LPAREN functionArguments? RPAREN
      (AS recordAlias=strictIdentifier? LPAREN recordColumn (COMMA recordColumn)* RPAREN
      | (WITH ORDINALITY)? tableAlias)                               #tableFunction
    | LATERAL? ROWS FROM LPAREN rowsFromItem (COMMA rowsFromItem)* RPAREN (WITH ORDINALITY)? tableAlias #rowsFromTableFunction
    | LATERAL? LPAREN VALUES valuesClause (COMMA valuesClause)* RPAREN tableAlias #valuesTable
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    | LATERAL LPAREN query RPAREN tableAlias                         #lateralQuery
    ;

xmlDocumentMode
    : DOCUMENT | CONTENT
    ;

xmlAttributes
    : XMLATTRIBUTES LPAREN xmlNamedExpression (COMMA xmlNamedExpression)* RPAREN
    ;

xmlNamedExpression
    : expression (AS identifier)?
    ;

xmlNamespaces
    : XMLNAMESPACES LPAREN xmlNamespace (COMMA xmlNamespace)* RPAREN
    ;

xmlNamespace
    : expression AS identifier
    ;

xmlPassingMode
    : BY (REF | VALUE)
    ;

xmlTableColumn
    : name=identifier (FOR ORDINALITY | dataType xmlTableColumnOption*)
    ;

xmlTableColumnOption
    : PATH expression
    | DEFAULT expression
    | NOT? NULL
    ;

jsonTableColumns
    : COLUMNS LPAREN jsonTableColumn (COMMA jsonTableColumn)* RPAREN
    ;

jsonTableColumn
    : name=identifier FOR ORDINALITY
    | name=identifier dataType (FORMAT JSON (ENCODING UTF8)?)? (PATH expression)?
      jsonWrapperClause? jsonQuotesClause? jsonQueryBehavior*
    | name=identifier dataType EXISTS (PATH expression)? ((ERROR | TRUE | FALSE | UNKNOWN) ON ERROR)?
    | NESTED PATH? expression (AS identifier)? jsonTableColumns
    ;

recordColumn
    : identifier dataType (COLLATE multipartIdentifier)?
    ;

rowsFromItem
    : functionName LPAREN functionArguments? RPAREN
      (AS? LPAREN (recordColumn (COMMA recordColumn)* | identifierList) RPAREN)?
    ;

tableSampleClause
    : TABLESAMPLE sampleMethod=qualifiedName LPAREN expressionList RPAREN
      (REPEATABLE LPAREN expression RPAREN)?
    ;

joinRelation
    : joinType? JOIN relationPrimary joinCriteria?
    | NATURAL naturalJoinType? JOIN relationPrimary
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

naturalJoinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    ;

joinCriteria
    : ON expression
    | USING LPAREN identifierList RPAREN (AS usingAlias=strictIdentifier)?
    ;

tableAlias
    : (AS? strictIdentifier (LPAREN identifierList RPAREN)?)?
    ;

whereClause
    : WHERE expression
    ;

cursorWhereClause
    : WHERE CURRENT OF identifier
    ;

groupByClause
    : GROUP BY (ALL | DISTINCT)? groupByItem (COMMA groupByItem)*
    ;

groupByItem
    : LPAREN expressionList? RPAREN
    | expression
    | ROLLUP LPAREN groupByItem (COMMA groupByItem)* RPAREN
    | CUBE LPAREN groupByItem (COMMA groupByItem)* RPAREN
    | GROUPING SETS LPAREN groupByItem (COMMA groupByItem)* RPAREN
    ;

havingClause
    : HAVING expression
    ;

queryOrganization
    : (ORDER BY sortItem (COMMA sortItem)*)?
      (lockingClause+ paginationClause? | paginationClause lockingClause*)?
    ;

paginationClause
    : (limitClause | fetchClause) offsetClause?
    | offsetClause (limitClause | fetchClause)?
    ;

limitClause
    : LIMIT (ALL | expression (COMMA expression)?)
    ;

offsetClause
    : OFFSET expression (ROW | ROWS)?
    ;

fetchClause
    : FETCH (FIRST | NEXT) expression? (ROW | ROWS) (ONLY | WITH TIES)
    ;

lockingClause
    : FOR rowLockMode (OF multipartIdentifierList)? (NOWAIT | SKIP_ LOCKED)?
    ;

rowLockMode
    : UPDATE
    | NO KEY UPDATE
    | SHARE
    | KEY SHARE
    ;

sortItem
    : expression (ASC | DESC | USING sortOperator)? (NULLS (FIRST | LAST))?
    ;

sortOperator
    : comparisonOperator
    | OPERATOR LPAREN (identifier DOT)? comparisonOperator RPAREN
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
    : NOT? BETWEEN (ASYMMETRIC | SYMMETRIC)? lower=valueExpression AND upper=valueExpression
    | NOT? IN LPAREN (expressionList | query) RPAREN
    | NOT? (LIKE | ILIKE | SIMILAR TO) valueExpression (ESCAPE valueExpression)?
    | ISNULL
    | NOTNULL
    | IS NOT? (NULL | TRUE | FALSE | UNKNOWN)
    | IS NOT? DISTINCT FROM valueExpression
    | IS NOT? JSON (VALUE | SCALAR | ARRAY | OBJECT)? jsonUniqueKeys?
    | IS NOT? DOCUMENT
    ;

valueExpression
    : primaryExpression                                              #valueExpressionDefault
    | operator=(MINUS | PLUS) valueExpression                        #unaryExpression
    | left=valueExpression operator=(STAR | SLASH | PERCENT) right=valueExpression   #arithmeticBinary
    | left=valueExpression operator=(PLUS | MINUS) right=valueExpression             #arithmeticBinaryPlusMinus
    | left=valueExpression CONCAT right=valueExpression              #concatExpression
    | left=valueExpression operator=(ARROW | ARROW_TEXT | HASH_ARROW | HASH_ARROW_TEXT | JSON_PATH_EXISTS | JSON_PATH_DELETE | CONTAINS | CONTAINED_BY | TS_MATCH | ARRAY_OVERLAP | QUESTION | QUESTION_PIPE | QUESTION_AMP | TILDE | TILDE_STAR | BANG_TILDE | BANG_TILDE_STAR) right=valueExpression #postgresOperatorExpression
    | valueExpression COLON COLON dataType                            #postgresCast
    | valueExpression AT TIME ZONE valueExpression                   #atTimeZoneExpression
    | valueExpression COLLATE multipartIdentifier                    #collateExpression
    | left=valueExpression comparisonOperator (ANY | SOME | ALL) LPAREN (query | expressionList) RPAREN #quantifiedComparison
    | left=valueExpression comparisonOperator right=valueExpression  #comparison
    ;

comparisonOperator
    : EQ | NEQ | LT | GT | LTE | GTE
    ;

primaryExpression
    : CASE whenClause+ (ELSE elseExpr=expression)? END              #searchedCase
    | CASE operand=expression whenClause+ (ELSE elseExpr=expression)? END  #simpleCase
    | CAST LPAREN expression AS dataType RPAREN                      #castExpr
    | ARRAY LBRACKET expressionList? RBRACKET                        #arrayConstructor
    | ARRAY LPAREN arrayConstructorQuery RPAREN                      #arraySubqueryConstructor
    | ROW LPAREN expressionList? RPAREN                              #rowConstructor
    | EXTRACT LPAREN identifier FROM expression RPAREN               #extractExpression
    | POSITION LPAREN expression IN expression RPAREN                #positionExpression
    | SUBSTRING LPAREN expression FROM expression (FOR expression)? RPAREN #substringExpression
    | TRIM LPAREN ((BOTH | LEADING | TRAILING) expression? FROM)? expression RPAREN #trimExpression
    | OVERLAY LPAREN expression PLACING expression FROM expression (FOR expression)? RPAREN #overlayExpression
    | XMLPARSE LPAREN xmlDocumentMode expression RPAREN             #xmlParseExpression
    | XMLSERIALIZE LPAREN xmlDocumentMode expression AS dataType (NO? INDENT)? RPAREN #xmlSerializeExpression
    | XMLEXISTS LPAREN expression PASSING xmlPassingMode? expression xmlPassingMode? RPAREN #xmlExistsExpression
    | XMLELEMENT LPAREN NAME identifier (COMMA xmlAttributes)? (COMMA expressionList)? RPAREN #xmlElementExpression
    | XMLFOREST LPAREN xmlNamedExpression (COMMA xmlNamedExpression)* RPAREN #xmlForestExpression
    | JSON_OBJECT LPAREN (jsonObjectPair (COMMA jsonObjectPair)*)?
      jsonNullClause? jsonUniqueKeys? jsonReturningClause? RPAREN    #jsonObjectExpression
    | JSON_ARRAY LPAREN arrayConstructorQuery jsonReturningClause? RPAREN #jsonArrayQueryExpression
    | JSON_ARRAY LPAREN (jsonInputValue (COMMA jsonInputValue)*)?
      jsonNullClause? jsonReturningClause? RPAREN                    #jsonArrayExpression
    | JSON LPAREN jsonInputValue jsonUniqueKeys? RPAREN              #jsonConstructorExpression
    | JSON_SERIALIZE LPAREN jsonInputValue jsonReturningClause? RPAREN #jsonSerializeExpression
    | JSON_VALUE LPAREN expression COMMA expression jsonPassingClause? (RETURNING dataType)?
      jsonValueBehavior* RPAREN                                     #jsonValueExpression
    | JSON_QUERY LPAREN expression COMMA expression jsonPassingClause?
      jsonReturningClause?
      jsonWrapperClause? jsonQuotesClause? jsonQueryBehavior* RPAREN                                     #jsonQueryExpression
    | JSON_EXISTS LPAREN expression COMMA expression jsonPassingClause?
      ((TRUE | FALSE | UNKNOWN | ERROR) ON ERROR)? RPAREN            #jsonExistsExpression
    | functionName LPAREN STAR RPAREN aggregateSuffix?               #functionCallStar
    | functionName LPAREN setQuantifier? functionArguments aggregateOrderBy? functionSeparator? RPAREN aggregateSuffix?  #functionCall
    | functionName LPAREN RPAREN aggregateSuffix?                    #functionCallEmpty
    | LPAREN query RPAREN                                            #scalarSubquery
    | LPAREN expression RPAREN                                       #parenthesizedExpression
    | primaryExpression LBRACKET expression? (COLON expression?)? RBRACKET #subscriptExpression
    | primaryExpression DOT identifier                                #dereference
    | POSITIONAL_PARAMETER                                           #positionalParameter
    | identifier                                                     #columnReference
    | number                                                         #numberLiteral
    | string                                                         #stringLiteral
    | NULL                                                           #nullLiteral
    | TRUE                                                           #booleanTrue
    | FALSE                                                          #booleanFalse
    | (DATE | TIME | TIMESTAMP) string                               #dateTimeLiteral
    | XML string                                                    #xmlLiteral
    | INTERVAL string identifier?                                    #intervalLiteral
    | DEFAULT                                                        #defaultLiteral
    ;

arrayConstructorQuery
    : query
    ;

jsonObjectPair
    : expression (VALUE | COLON) jsonInputValue
    ;

jsonInputValue
    : expression jsonFormatClause?
    ;

jsonFormatClause
    : FORMAT JSON (ENCODING UTF8)?
    ;

jsonReturningClause
    : RETURNING dataType jsonFormatClause?
    ;

jsonNullClause
    : (NULL | ABSENT) ON NULL
    ;

jsonUniqueKeys
    : (WITH | WITHOUT) UNIQUE KEYS?
    ;

jsonPassingClause
    : PASSING jsonPassingArgument (COMMA jsonPassingArgument)*
    ;

jsonPassingArgument
    : expression AS identifier
    ;

jsonValueBehavior
    : (ERROR | NULL | DEFAULT expression) ON (EMPTY | ERROR)
    ;

jsonWrapperClause
    : (WITHOUT | WITH (CONDITIONAL | UNCONDITIONAL)?) ARRAY? WRAPPER
    ;

jsonQuotesClause
    : (KEEP | OMIT) QUOTES (ON SCALAR STRING_KEYWORD)?
    ;

jsonQueryBehavior
    : (ERROR | NULL | EMPTY (ARRAY | OBJECT)? | DEFAULT expression) ON (EMPTY | ERROR)
    ;

aggregateOrderBy
    : ORDER BY sortItem (COMMA sortItem)*
    ;

aggregateSuffix
    : withinGroupClause filterClause? (OVER windowRef)?
    | filterClause (OVER windowRef)?
    | OVER windowRef
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
    : LPAREN identifier? (PARTITION BY expressionList)? (ORDER BY sortItem (COMMA sortItem)*)? windowFrame? RPAREN
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
    : (ROWS | RANGE | GROUPS) windowFrameExtent
      (EXCLUDE (CURRENT ROW | GROUP | TIES | NO OTHERS))?
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
    : qualifiedName
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

functionArguments
    : callArgument (COMMA callArgument)*
    ;

callArgument
    : (identifier NAMED_ARGUMENT)? expression
    ;

functionSeparator
    : SEPARATOR expression
    ;

// ============ DML Statements ============

insertStatement
    : ctes? INSERT INTO? TABLE? multipartIdentifier (AS targetAlias=strictIdentifier)?
      (LPAREN columnList=identifierList RPAREN)?
      (OVERRIDING (SYSTEM | USER) VALUE)?
      (query | VALUES valuesClause (COMMA valuesClause)* | DEFAULT VALUES | SET assignmentList)
      onConflictClause?
      returningClause?
    ;

onConflictClause
    : ON CONFLICT conflictTarget? indexPredicate=conflictWhereClause?
      DO (NOTHING | UPDATE SET assignmentList actionPredicate=conflictWhereClause?)
    ;

conflictTarget
    : LPAREN conflictIndexElement (COMMA conflictIndexElement)* RPAREN
    | ON CONSTRAINT identifier
    ;

conflictIndexElement
    : (identifier | LPAREN expression RPAREN)
      (COLLATE multipartIdentifier)? multipartIdentifier?
    ;

conflictWhereClause
    : WHERE expression
    ;

returningClause
    : RETURNING (WITH LPAREN returningRowAlias (COMMA returningRowAlias)* RPAREN)?
      returningItem (COMMA returningItem)*
    ;

returningRowAlias
    : (OLD | NEW) AS alias=identifier
    ;

returningItem
    : expression (AS? alias=identifier)?
    | qualifiedName DOT STAR
    | STAR
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

updateStatement
    : ctes? UPDATE relation SET assignmentList fromClause? (whereClause | cursorWhereClause)? returningClause?
    ;

deleteStatement
    : ctes? DELETE FROM inheritanceRelation tableAlias (USING relationList)? (whereClause | cursorWhereClause)? returningClause?   #deleteFrom
    | ctes? DELETE multipartIdentifier FROM relationList whereClause?                        #deleteAlias
    ;

mergeStatement
    : ctes? MERGE INTO? targetRelation=inheritanceRelation tableAlias
      USING relationPrimary
      ON expression
      mergeClause+
      returningClause?
    ;

mergeClause
    : WHEN (MATCHED | NOT MATCHED BY SOURCE) mergeCondition? THEN mergeMatchedAction
    | WHEN NOT MATCHED (BY TARGET)? mergeCondition? THEN mergeNotMatchedAction
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
    : INSERT (LPAREN identifierList RPAREN)? (OVERRIDING (SYSTEM | USER) VALUE)?
      (VALUES LPAREN expressionList RPAREN | DEFAULT VALUES)
    | DO NOTHING
    ;

assignmentList
    : assignment (COMMA assignment)*
    ;

assignment
    : multipartIdentifier assignmentSubscript* EQ expression
    | LPAREN identifierList RPAREN EQ (ROW? LPAREN expressionList RPAREN | LPAREN query RPAREN)
    ;

assignmentSubscript
    : LBRACKET (expression | expression? COLON expression?) RBRACKET
    ;

// ============ DDL Statements ============

createIndexStatement
    : CREATE UNIQUE? INDEX CONCURRENTLY? ((IF NOT EXISTS)? multipartIdentifier)?
      ON ONLY? target=multipartIdentifier (USING identifier)?
      LPAREN indexElementList RPAREN
      (INCLUDE LPAREN includeColumns=identifierList RPAREN)?
      (NULLS NOT? DISTINCT)?
      relationStorageParameters?
      (TABLESPACE identifier)?
      (WHERE expression)?
    ;

alterIndexStatement
    : ALTER INDEX (IF EXISTS)? source=multipartIdentifier RENAME TO target=identifier                #alterIndexRename
    | ALTER INDEX (IF EXISTS)? source=multipartIdentifier SET SCHEMA targetSchema=identifier         #alterIndexSetSchema
    | ALTER INDEX (IF EXISTS)? source=multipartIdentifier .+?                                        #alterIndexOther
    ;

dropIndexStatement
    : DROP INDEX CONCURRENTLY? (IF EXISTS)? multipartIdentifierList (CASCADE | RESTRICT)?
    ;

statisticsStatement
    : CREATE STATISTICS (IF NOT EXISTS)? multipartIdentifier? statisticsKindList?
      ON expressionList FROM table=multipartIdentifier
    | DROP STATISTICS (IF EXISTS)? multipartIdentifierList (CASCADE | RESTRICT)?
    ;

statisticsKindList
    : LPAREN identifierList RPAREN
    ;

typeStatement
    : CREATE TYPE multipartIdentifier (AS typeDefinition | LPAREN typeOption (COMMA typeOption)* RPAREN)?
    | ALTER TYPE multipartIdentifier alterTypeAction
    | DROP TYPE (IF EXISTS)? multipartIdentifierList (CASCADE | RESTRICT)?
    ;

typeDefinition
    : ENUM LPAREN (string (COMMA string)*)? RPAREN
    | LPAREN (typeAttribute (COMMA typeAttribute)*)? RPAREN
    | RANGE LPAREN typeOption (COMMA typeOption)* RPAREN
    ;

typeAttribute
    : identifier dataType (COLLATE multipartIdentifier)?
    ;

typeOption
    : (identifier | LIKE) (EQ (dataType | number | string))?
    ;

alterTypeAction
    : ADD VALUE (IF NOT EXISTS)? string ((BEFORE | AFTER) string)?
    | RENAME VALUE string TO string
    | RENAME ATTRIBUTE identifier TO identifier (CASCADE | RESTRICT)?
    | alterTypeAttribute (COMMA alterTypeAttribute)*
    | RENAME TO identifier
    | OWNER TO identifier
    | SET SCHEMA identifier
    | SET LPAREN typeOption (COMMA typeOption)* RPAREN
    ;

alterTypeAttribute
    : (ADD ATTRIBUTE typeAttribute
      | DROP ATTRIBUTE (IF EXISTS)? identifier
      | ALTER ATTRIBUTE identifier (SET DATA)? TYPE dataType (COLLATE multipartIdentifier)?)
      (CASCADE | RESTRICT)?
    ;

domainStatement
    : CREATE DOMAIN multipartIdentifier AS? dataType (COLLATE multipartIdentifier)?
      (DEFAULT expression)? domainConstraint*
    | ALTER DOMAIN multipartIdentifier alterDomainAction
    | DROP DOMAIN (IF EXISTS)? multipartIdentifierList (CASCADE | RESTRICT)?
    ;

domainConstraint
    : (CONSTRAINT identifier)? (NOT? NULL | CHECK LPAREN expression RPAREN)
    ;

alterDomainAction
    : SET DEFAULT expression
    | DROP DEFAULT
    | (SET | DROP) NOT NULL
    | ADD domainConstraint (NOT VALID)?
    | DROP CONSTRAINT (IF EXISTS)? identifier (CASCADE | RESTRICT)?
    | RENAME (CONSTRAINT identifier)? TO identifier
    | VALIDATE CONSTRAINT identifier
    | OWNER TO identifier
    | SET SCHEMA identifier
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

databaseStatement
    : CREATE DATABASE identifier .*
    | ALTER DATABASE identifier .*
    | DROP DATABASE (IF EXISTS)? identifier
    ;

createFunctionStatement
    : CREATE (OR REPLACE)? (FUNCTION | PROCEDURE) multipartIdentifier LPAREN functionArgumentList? RPAREN
      routineReturns? routineOption* (AS string (COMMA string)? routineOption* | routineSqlBody)
    ;

routineSqlBody
    : RETURN expression
    | BEGIN ATOMIC (routineSqlStatement SEMI)* END
    ;

routineSqlStatement
    : cteBody
    | RETURN expression
    ;

routineReturns
    : RETURNS (SETOF? dataType | TABLE LPAREN routineResultColumn (COMMA routineResultColumn)* RPAREN)
    ;

routineResultColumn
    : identifier dataType
    ;

routineOption
    : LANGUAGE (identifier | string)
    | TRANSFORM FOR TYPE dataType (COMMA FOR TYPE dataType)*
    | WINDOW
    | (IMMUTABLE | STABLE | VOLATILE)
    | CALLED ON NULL INPUT
    | RETURNS NULL ON NULL INPUT
    | STRICT
    | EXTERNAL? SECURITY (DEFINER | INVOKER)
    | PARALLEL (SAFE | UNSAFE | RESTRICTED)
    | NOT? LEAKPROOF
    | (COST | ROWS) number
    | SUPPORT multipartIdentifier
    | SET multipartIdentifier ((TO | EQ) expressionList | FROM CURRENT)
    ;

triggerStatement
    : CREATE (OR REPLACE)? TRIGGER identifier .+? ON target=multipartIdentifier .+?   #createTrigger
    | DROP TRIGGER (IF EXISTS)? identifier ON target=multipartIdentifier (CASCADE | RESTRICT)? #dropTrigger
    | CREATE EVENT TRIGGER identifier ON identifier .+?                                #createEventTrigger
    | ALTER EVENT TRIGGER identifier .+?                                               #alterEventTrigger
    | DROP EVENT TRIGGER (IF EXISTS)? identifier (CASCADE | RESTRICT)?                 #dropEventTrigger
    ;

ruleStatement
    : CREATE (OR REPLACE)? RULE identifier AS ON (SELECT | INSERT | UPDATE | DELETE)
      TO target=multipartIdentifier (WHERE expression)? DO (ALSO | INSTEAD)? .+?       #createRule
    | DROP RULE (IF EXISTS)? identifier ON target=multipartIdentifier (CASCADE | RESTRICT)? #dropRule
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

publicationStatement
    : CREATE PUBLICATION identifier (FOR publicationTarget)? publicationOptions?
    | ALTER PUBLICATION identifier (ADD | SET | DROP) publicationTarget publicationOptions?
    | ALTER PUBLICATION identifier SET publicationOptionList
    | ALTER PUBLICATION identifier RENAME TO identifier
    | ALTER PUBLICATION identifier OWNER TO identifier
    | DROP PUBLICATION (IF EXISTS)? identifierList (CASCADE | RESTRICT)?
    ;

publicationTarget
    : ALL TABLES
    | publicationObject (COMMA publicationObject)*
    ;

publicationObject
    : TABLE publicationTable (COMMA publicationTable)*
    | TABLES IN SCHEMA identifierList
    ;

publicationTable
    : (multipartIdentifier STAR? | ONLY (multipartIdentifier | LPAREN multipartIdentifier RPAREN))
      (LPAREN identifierList RPAREN)?
      (WHERE LPAREN expression RPAREN)?
    ;

publicationOptions
    : WITH publicationOptionList
    ;

publicationOptionList
    : LPAREN publicationOption (COMMA publicationOption)* RPAREN
    ;

publicationOption
    : identifier (EQ (identifier | string | number | TRUE | FALSE))?
    ;

subscriptionStatement
    : CREATE SUBSCRIPTION identifier CONNECTION string PUBLICATION identifierList publicationOptions?
    | ALTER SUBSCRIPTION identifier .+?
    | DROP SUBSCRIPTION (IF EXISTS)? identifier (CASCADE | RESTRICT)?
    ;

foreignDataStatement
    : CREATE FOREIGN TABLE (IF NOT EXISTS)? multipartIdentifier (LPAREN tableElementList RPAREN)? SERVER identifier foreignOptions?
    | ALTER FOREIGN TABLE (IF EXISTS)? multipartIdentifier .+?
    | DROP FOREIGN TABLE (IF EXISTS)? multipartIdentifierList (CASCADE | RESTRICT)?
    | IMPORT FOREIGN SCHEMA identifier importForeignSchemaFilter? FROM SERVER identifier INTO identifier foreignOptions?
    | CREATE FOREIGN DATA WRAPPER identifier .+?
    | ALTER FOREIGN DATA WRAPPER identifier .+?
    | DROP FOREIGN DATA WRAPPER (IF EXISTS)? identifier (CASCADE | RESTRICT)?
    | CREATE SERVER (IF NOT EXISTS)? identifier .+?
    | ALTER SERVER identifier .+?
    | DROP SERVER (IF EXISTS)? identifier (CASCADE | RESTRICT)?
    | CREATE USER MAPPING (IF NOT EXISTS)? FOR .+? SERVER identifier foreignOptions?
    | ALTER USER MAPPING FOR .+? SERVER identifier .+?
    | DROP USER MAPPING (IF EXISTS)? FOR .+? SERVER identifier
    ;

importForeignSchemaFilter
    : LIMIT TO LPAREN identifierList RPAREN
    | EXCEPT LPAREN identifierList RPAREN
    ;

foreignOptions
    : OPTIONS LPAREN propertyList? RPAREN
    ;

functionArgumentList
    : functionArgument (COMMA functionArgument)*
    ;

functionArgument
    : (IN | OUT | INOUT | VARIADIC)? identifier? dataType ((DEFAULT | EQ) expression)?
    ;

indexElementList
    : indexElement (COMMA indexElement)*
    ;

indexElement
    : expression (multipartIdentifier (LPAREN storageParameter (COMMA storageParameter)* RPAREN)?)?
      (ASC | DESC)? (NULLS (FIRST | LAST))?
    ;

relationStorageParameters
    : WITH LPAREN storageParameter (COMMA storageParameter)* RPAREN
    ;

storageParameter
    : identifier (EQ copyOptionValue)?
    ;

createTableStatement
    : CREATE (TEMPORARY | TEMP | UNLOGGED)? TABLE (IF NOT EXISTS)? target=multipartIdentifier PARTITION OF partitionParent=multipartIdentifier partitionBound?
    | CREATE (TEMPORARY | TEMP | UNLOGGED)? TABLE (IF NOT EXISTS)? multipartIdentifier
      LPAREN ctasColumnList=identifierList RPAREN
      tableOption* createTableStorageClause
      AS query withDataClause?
    | CREATE (TEMPORARY | TEMP | UNLOGGED)? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      inheritsClause?
      partitionByClause?
      commentClause?
      tableOption* createTableStorageClause
      (AS query withDataClause?)?
    | CREATE (TEMPORARY | TEMP | UNLOGGED)? TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

createTableStorageClause
    : (USING identifier)?
      relationStorageParameters?
      (ON COMMIT (PRESERVE ROWS | DELETE ROWS | DROP))?
      (TABLESPACE identifier)?
    ;

inheritsClause
    : INHERITS LPAREN multipartIdentifierList RPAREN
    ;

partitionByClause
    : PARTITION BY identifier LPAREN expressionList RPAREN
    ;

partitionBound
    : FOR VALUES partitionBoundKind
    | DEFAULT
    ;

partitionBoundKind
    : FROM LPAREN expressionList RPAREN TO LPAREN expressionList RPAREN
    | IN LPAREN expressionList RPAREN
    | WITH LPAREN (hashPartitionBound (COMMA hashPartitionBound)* | expressionList) RPAREN
    ;

hashPartitionBound
    : (MODULUS | REMAINDER) number
    ;

createViewStatement
    : CREATE (OR REPLACE)? (TEMPORARY | TEMP)? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      relationStorageParameters?
      AS query (WITH (CASCADED | LOCAL)? CHECK OPTION)?
    | CREATE MATERIALIZED VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      (USING identifier)? relationStorageParameters? (TABLESPACE identifier)?
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
    : REFRESH MATERIALIZED VIEW CONCURRENTLY? multipartIdentifier withDataClause?
    ;

alterViewStatement
    : ALTER VIEW (IF EXISTS)? source=multipartIdentifier RENAME TO target=identifier                 #alterViewRename
    | ALTER VIEW (IF EXISTS)? source=multipartIdentifier SET SCHEMA targetSchema=identifier          #alterViewSetSchema
    | ALTER VIEW (IF EXISTS)? source=multipartIdentifier .+?                                         #alterViewOther
    ;

alterMaterializedViewStatement
    : ALTER MATERIALIZED VIEW (IF EXISTS)? source=multipartIdentifier RENAME TO target=identifier   #alterMaterializedViewRename
    | ALTER MATERIALIZED VIEW (IF EXISTS)? source=multipartIdentifier SET SCHEMA targetSchema=identifier #alterMaterializedViewSetSchema
    | ALTER MATERIALIZED VIEW (IF EXISTS)? source=multipartIdentifier .+?                           #alterMaterializedViewOther
    ;

truncateTableStatement
    : TRUNCATE TABLE? inheritanceRelation (COMMA inheritanceRelation)*
      ((RESTART | CONTINUE) IDENTITY)? (CASCADE | RESTRICT)?
    ;

alterTableStatement
    : ALTER TABLE (IF EXISTS)? parent=multipartIdentifier ATTACH PARTITION child=multipartIdentifier partitionBound?  #alterTableAttachPartition
    | ALTER TABLE (IF EXISTS)? parent=multipartIdentifier DETACH PARTITION child=multipartIdentifier (CONCURRENTLY | FINALIZE)?                  #alterTableDetachPartition
    | ALTER TABLE target=multipartIdentifier RENAME COLUMN sourceColumn=identifier TO targetColumn=identifier #alterTableRenameColumn
    | ALTER TABLE multipartIdentifier RENAME (TO | AS)? multipartIdentifier   #alterTableRename
    | ALTER TABLE target=multipartIdentifier DROP COLUMN (IF EXISTS)? dropColumn=identifier (CASCADE | RESTRICT)? #alterTableDropColumn
    | ALTER TABLE (IF EXISTS)? ONLY? target=multipartIdentifier ALTER COLUMN? alterColumn=identifier
      SET EXPRESSION AS LPAREN generatedExpression=expression RPAREN #alterTableSetGeneratedExpression
    | ALTER TABLE (IF EXISTS)? ONLY? target=multipartIdentifier ALTER COLUMN? alterColumn=identifier
      DROP EXPRESSION (IF EXISTS)? #alterTableDropGeneratedExpression
    | ALTER TABLE target=multipartIdentifier ALTER COLUMN? alterColumn=identifier SET DEFAULT expression #alterTableAlterColumnSetDefault
    | ALTER TABLE target=multipartIdentifier ALTER COLUMN? alterColumn=identifier DROP DEFAULT #alterTableAlterColumnDropDefault
    | ALTER TABLE target=multipartIdentifier ALTER COLUMN? alterColumn=identifier SET NOT NULL #alterTableAlterColumnSetNotNull
    | ALTER TABLE target=multipartIdentifier ALTER COLUMN? alterColumn=identifier DROP NOT NULL #alterTableAlterColumnDropNotNull
    | ALTER TABLE target=multipartIdentifier ALTER COLUMN? alterColumn=identifier TYPE dataType (USING usingExpression=expression)? #alterTableAlterColumnType
    | ALTER TABLE target=multipartIdentifier ADD (CONSTRAINT identifier)? FOREIGN KEY LPAREN localColumns=identifierList RPAREN REFERENCES refTable=multipartIdentifier (LPAREN refColumns=identifierList RPAREN)? #alterTableAddForeignKey
    | ALTER TABLE target=multipartIdentifier ADD (CONSTRAINT identifier)? CHECK LPAREN checkExpression=expression RPAREN #alterTableAddCheck
    | ALTER TABLE (IF EXISTS)? multipartIdentifier ADD COLUMN? (IF NOT EXISTS)?
      columnName=identifier dataType (COLLATE qualifiedName)? columnConstraint* #alterTableAddColumn
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

securityLabelStatement
    : SECURITY LABEL (FOR identifier)? ON (TABLE multipartIdentifier | COLUMN multipartIdentifier)
      IS string
    ;

copyStatement
    : COPY multipartIdentifier (LPAREN columnList=identifierList RPAREN)?
      FROM copySource copyOptions? whereClause?                                  #copyFromTable
    | COPY multipartIdentifier (LPAREN columnList=identifierList RPAREN)?
      TO copyTarget copyOptions?                                    #copyToTable
    | COPY LPAREN cteBody RPAREN TO copyTarget copyOptions?           #copyToQuery
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
    : identifier (EQ? (copyOptionValue | LPAREN identifierList RPAREN | STAR))?
    ;

copyOptionValue
    : identifier
    | string
    | number
    | TRUE
    | FALSE
    ;

vacuumStatement
    : VACUUM (maintenanceOptions | (FULL | FREEZE | VERBOSE | ANALYZE)+)?
      (maintenanceRelation (COMMA maintenanceRelation)*)?
    ;

analyzeStatement
    : ANALYZE (VERBOSE | maintenanceOptions)? (maintenanceRelation (COMMA maintenanceRelation)*)?
    ;

maintenanceOptions
    : LPAREN maintenanceOption (COMMA maintenanceOption)* RPAREN
    ;

maintenanceOption
    : (identifier | FULL) (copyOptionValue | ON)?
    ;

maintenanceRelation
    : multipartIdentifier (LPAREN identifierList RPAREN)?
    ;

reindexStatement
    : REINDEX maintenanceOptions?
      ((TABLE | INDEX | SCHEMA) CONCURRENTLY? multipartIdentifier
      | (DATABASE | SYSTEM) CONCURRENTLY? multipartIdentifier?)
    ;

clusterStatement
    : CLUSTER VERBOSE
    | CLUSTER VERBOSE? multipartIdentifier (USING multipartIdentifier)?
    ;

lockStatement
    : LOCK TABLE? lockTarget (COMMA lockTarget)* (IN lockMode MODE)? NOWAIT?
    ;

lockTarget
    : ONLY? multipartIdentifier STAR?
    ;

lockMode
    : lockModeWord+
    ;

lockModeWord
    : ACCESS
    | EXCLUSIVE
    | SHARE
    | ROW
    | UPDATE
    | identifier
    ;

routineControlStatement
    : CALL .+?
    | PREPARE identifier (LPAREN dataTypeList? RPAREN)? AS preparedStatement=cteBody
    | EXECUTE identifier (LPAREN expressionList? RPAREN)?
    | DEALLOCATE PREPARE? identifier
    | DECLARE identifier cursorOption* CURSOR cursorHold? FOR cursorQuery=query
    | FETCH fetchDirection? ((FROM | IN) identifier)?
    | MOVE fetchDirection? ((FROM | IN) identifier)?
    | CLOSE (identifier | ALL)
    | DO (string | DOLLAR_QUOTED_STRING) (LANGUAGE identifier)?
    | LISTEN identifier
    | NOTIFY identifier (COMMA string)?
    | UNLISTEN (STAR | identifier)
    ;

cursorOption
    : BINARY
    | INSENSITIVE
    | SCROLL
    | NO SCROLL
    ;

cursorHold
    : WITH HOLD
    | WITHOUT HOLD
    ;

fetchDirection
    : NEXT
    | PRIOR
    | FIRST
    | LAST
    | ABSOLUTE number
    | RELATIVE number
    | FORWARD number?
    | BACKWARD number?
    | ALL
    ;

principalStatement
    : CREATE (ROLE | USER) .+?
    | ALTER (ROLE | USER) .+?
    | DROP (ROLE | USER) (IF EXISTS)? .+?
    ;

defaultPrivilegesStatement
    : ALTER DEFAULT PRIVILEGES .+?
    ;

explainStatement
    : EXPLAIN explainOption* explainedStatement
    | EXPLAIN LPAREN explainOption (COMMA explainOption)* RPAREN explainedStatement
    ;

explainOption
    : (ANALYZE | VERBOSE | COSTS | SETTINGS | BUFFERS | WAL | TIMING | SUMMARY | FORMAT) (identifier | string | number | TRUE | FALSE)?
    ;

explainedStatement
    : query
    | insertStatement
    | updateStatement
    | deleteStatement
    | mergeStatement
    ;

transactionStatement
    : BEGIN (WORK | TRANSACTION)? transactionCharacteristicList?
    | START TRANSACTION transactionCharacteristicList?
    | COMMIT (WORK | TRANSACTION)? transactionChain?
    | ROLLBACK (WORK | TRANSACTION)? transactionChain?
    | ROLLBACK (WORK | TRANSACTION)? TO SAVEPOINT? identifier
    | SAVEPOINT identifier
    | RELEASE SAVEPOINT? identifier
    | SET TRANSACTION transactionCharacteristicList
    ;

transactionChain
    : AND NO? CHAIN
    ;

transactionCharacteristicList
    : transactionCharacteristic (COMMA transactionCharacteristic)*
    ;

transactionCharacteristic
    : ISOLATION LEVEL isolationLevel
    | READ (WRITE | ONLY)
    | NOT? DEFERRABLE
    ;

isolationLevel
    : READ (UNCOMMITTED | COMMITTED)
    | REPEATABLE READ
    | SERIALIZABLE
    ;

grantStatement
    : GRANT .+? ON privilegeTarget TO .+?
    ;

revokeStatement
    : REVOKE .+? ON privilegeTarget FROM .+?
    ;

roleGrantStatement
    : GRANT identifierList TO identifierList
    | REVOKE identifierList FROM identifierList
    ;

privilegeTarget
    : TABLE? multipartIdentifier                                     #tablePrivilegeTarget
    | SEQUENCE multipartIdentifier                                   #namedControlPrivilegeTarget
    | (FUNCTION | PROCEDURE) multipartIdentifier (LPAREN dataTypeList? RPAREN)? #routinePrivilegeTarget
    | SCHEMA multipartIdentifier                                     #namedControlPrivilegeTarget
    | ALL (TABLES | SEQUENCES | FUNCTIONS) IN SCHEMA identifier      #schemaWidePrivilegeTarget
    ;

setStatement
    : SET (SEARCH_PATH | identifier) (TO | EQ) expressionList
    ;

resetStatement
    : RESET (SEARCH_PATH | identifier | ALL)
    ;

discardStatement
    : DISCARD (ALL | PLANS | SEQUENCES | TEMP | TEMPORARY)
    ;

// ============ DDL Helpers ============

tableElementList
    : tableElement (COMMA tableElement)*
    ;

tableElement
    : LIKE multipartIdentifier likeOption*
    | columnName=identifier dataType columnConstraint*
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
    | GENERATED (ALWAYS | BY DEFAULT) AS IDENTITY
    | GENERATED ALWAYS AS LPAREN generatedExpression=expression RPAREN (STORED | VIRTUAL)?
    | REFERENCES refTable=multipartIdentifier (LPAREN identifierList RPAREN)?
    | CHECK LPAREN checkExpression=expression RPAREN
    | CONSTRAINT identifier nestedColumnConstraint=columnConstraint
    | AUTO_INCREMENT
    | PRIMARY KEY
    | UNIQUE KEY?
    ;

tableConstraint
    : (CONSTRAINT identifier)? PRIMARY KEY LPAREN identifierList RPAREN
    | UNIQUE (KEY | INDEX)? identifier? LPAREN identifierList RPAREN
    | (KEY | INDEX) identifier? LPAREN identifierList RPAREN
    | (CONSTRAINT identifier)? FOREIGN KEY LPAREN identifierList RPAREN REFERENCES refTable=multipartIdentifier (LPAREN identifierList RPAREN)?
    | (CONSTRAINT identifier)? CHECK LPAREN checkExpression=expression RPAREN
    ;

commentClause
    : COMMENT string
    ;

propertyList
    : property (COMMA property)*
    ;

property
    : (identifier | string) EQ? (identifier | string | number | TRUE | FALSE)
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
    : (qualifiedName typePrecision?
      | identifier LT dataType (COMMA dataType)* GT
      | (TIME | TIMESTAMP) typePrecision? (WITH | WITHOUT) TIME ZONE
      | CHARACTER VARYING typePrecision?
      | DOUBLE PRECISION)
      (ARRAY (LBRACKET NUMBER_LITERAL? RBRACKET)? | (LBRACKET NUMBER_LITERAL? RBRACKET)+)?
    ;

typePrecision
    : LPAREN NUMBER_LITERAL (COMMA NUMBER_LITERAL)* RPAREN
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
    : ACCESS | ADD | AFTER | ALSO | ALWAYS | ARRAY | ASC | AT | ATTRIBUTE | AUTO_INCREMENT | BEFORE | BOTH | BUFFERS | CALL | CAST | CASCADE | CHARSET | CHARACTER | CLUSTER | COLLATE | CONCURRENTLY | COSTS
    | ANALYZE | ANY | BINARY | COLUMN | COMMENT | CONNECTION | CONSTRAINT | COPY | CSV | CURSOR | DATABASE | DEALLOCATE | DECLARE | DEFAULT | DELIMITER | DESCRIBE | DESC | DOMAIN | END
    | ATTACH | BACKWARD | CHECK | CLOSE | CUBE | DETACH | ENGINE | ENUM | EVENT | EXCLUDING | EXECUTE | EXISTS | EXPLAIN | EXTENSION | EXTERNAL | EXTRACT | FALSE | FETCH | FILTER | FIRST | FOLLOWING | FOR | FORWARD | FOREIGN | FORMAT | FUNCTION | FUNCTIONS | GENERATED | GRANT | GROUPING | GROUPS | HEADER | HOLD | IF | ILIKE | IDENTITY | IMPORT | INCLUDE | INCLUDING | INDEX | INHERITS | INSENSITIVE | INTERVAL | KEY | LANGUAGE | LAST | LATERAL | LEADING | LIMIT | MAPPING | MATCHED | MATERIALIZED | MOVE | NEXT | NULL | NULLS
    | DATE | LABEL | LISTEN | LOCK | LOCKED | LOGGED | MODE | NOTIFY | NOWAIT | OF | OFFSET | ONLY | OPTIONS | ORDINALITY | OVERLAY | OVERRIDING | OVER | PARTITION | PERMISSIVE | PLACING | POLICY | OWNER | VALID | VALIDATE | POSITION | PRECEDING | PRIVILEGES | PUBLICATION | RANGE | RECURSIVE | REPLACE | RENAME | RESTRICT | RESTRICTIVE | REVOKE | ROLE | ROW | ROWS | RULE | SECURITY | SEPARATOR | SERVER | SHARE | SIMILAR | SKIP_ | SUBSTRING | TIME | TIMESTAMP | TRAILING | TRIM
    | ENABLE | DISABLE | FORCE | SOME | SOURCE | TARGET | OPERATOR | TABLESPACE | PRESERVE | CASCADED | LOCAL | OPTION | EXCLUDE | OTHERS | ESCAPE | JSON_VALUE | JSON_EXISTS | JSON_QUERY | JSON_TABLE | JSON_OBJECT | JSON_ARRAY | JSON_SERIALIZE | ABSENT | KEYS | COLUMNS | PATH | NESTED | JSON | ENCODING | UTF8 | CONDITIONAL | UNCONDITIONAL | QUOTES | OMIT | KEEP | OBJECT | SCALAR | STRING_KEYWORD | PASSING | XMLTABLE | XMLNAMESPACES | XML | XMLPARSE | XMLSERIALIZE | XMLEXISTS | XMLELEMENT | XMLATTRIBUTES | XMLFOREST | DOCUMENT | CONTENT | INDENT | NAME | REF | ERROR | EMPTY | FINALIZE | MODULUS | REMAINDER | CHAIN | DEFERRABLE | SEARCH | CYCLE | BREADTH | DEPTH | RESTART | CONTINUE | VARYING | DOUBLE | PRECISION
    | OLD | NEW | ABSOLUTE | EXCLUSIVE | FREEZE | INSTEAD | PREPARE | PRIMARY | PRIOR | PROCEDURE | PROGRAM | REFERENCES | REFRESH | REINDEX | RELATIVE | RESET | RETURNS | ROLLUP | SCHEMA | SCHEMAS | SCROLL | SEARCH_PATH | SEQUENCE | SEQUENCES | SET | SETS | SETTINGS | BEGIN | START | STATISTICS | STORED | SUMMARY | TIMING | TRANSACTION | COMMIT | ROLLBACK | SAVEPOINT | RELEASE | WORK | ISOLATION | LEVEL | READ | WRITE | REPEATABLE | BERNOULLI | COMMITTED | UNCOMMITTED | SERIALIZABLE | SHOW | STDIN | STDOUT | SUBSCRIPTION | SYSTEM | TABLE | TABLES | TABLESAMPLE | TEMP | TEMPORARY | TO | TRIGGER | TRUE | TRUNCATE | TYPE | UNBOUNDED | UNLISTEN | UNLOGGED | UNIQUE | USER | VACUUM | VALUE | VALUES | VERBOSE | VIEW | WAL | WINDOW | WITHIN | WITHOUT | WRAPPER | ZONE | DISCARD | PLANS
    | CALLED | COST | DEFINER | IMMUTABLE | INOUT | INPUT | INVOKER | LEAKPROOF | OUT | PARALLEL | RESTRICTED | SAFE | SETOF | STABLE | STRICT | SUPPORT | TRANSFORM | UNSAFE | VARIADIC | VOLATILE
    | ATOMIC | RETURN
    | VIRTUAL | EXPRESSION
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    | DOLLAR_QUOTED_STRING
    ;
