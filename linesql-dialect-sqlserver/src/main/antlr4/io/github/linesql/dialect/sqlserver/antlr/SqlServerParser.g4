parser grammar SqlServerParser;

options { tokenVocab = SqlServerLineageLexer; }

singleStatement
    : statement SEMI? EOF
    ;

statement
    : query                                                          #statementDefault
    | insertStatement                                                #insertStmt
    | updateStatisticsStatement                                      #updateStatisticsStmt
    | updateStatement                                                #updateStmt
    | deleteStatement                                                #deleteStmt
    | mergeStatement                                                 #mergeStmt
    | createStatisticsStatement                                      #createStatisticsStmt
    | createIndexStatement                                           #createIndexStmt
    | alterIndexStatement                                            #alterIndexStmt
    | dropStatisticsStatement                                        #dropStatisticsStmt
    | dropIndexStatement                                             #dropIndexStmt
    | databaseStatement                                              #databaseStmt
    | createSchemaStatement                                          #createSchemaStmt
    | alterSchemaStatement                                           #alterSchemaStmt
    | typeStatement                                                  #typeStmt
    | createSynonymStatement                                         #createSynonymStmt
    | createProcedureStatement                                       #createProcedureStmt
    | createTriggerStatement                                         #createTriggerStmt
    | createTableStatement                                           #createTableStmt
    | declareTableVariableStatement                                  #declareTableVariableStmt
    | declareScalarVariableStatement                                 #declareScalarVariableStmt
    | createViewStatement                                            #createViewStmt
    | alterViewStatement                                             #alterViewStmt
    | dropSchemaStatement                                            #dropSchemaStmt
    | dropSynonymStatement                                           #dropSynonymStmt
    | dropProcedureStatement                                         #dropProcedureStmt
    | dropTriggerStatement                                           #dropTriggerStmt
    | dropTableStatement                                             #dropTableStmt
    | dropViewStatement                                              #dropViewStmt
    | truncateTableStatement                                         #truncateTableStmt
    | alterTableStatement                                            #alterTableStmt
    | useStatement                                                   #useStmt
    | setStatement                                                   #setStmt
    | executeStatement                                               #executeStmt
    | transactionStatement                                           #transactionStmt
    | backupRestoreStatement                                         #backupRestoreStmt
    | dbccStatement                                                  #dbccStmt
    | scriptControlStatement                                         #scriptControlStmt
    | grantStatement                                                 #grantStmt
    | revokeStatement                                                #revokeStmt
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
    : selectClause selectIntoClause? fromClause? whereClause? groupByClause? havingClause? queryHintClause?
    ;

selectClause
    : SELECT setQuantifier? topClause? selectItemList
    ;

selectIntoClause
    : INTO multipartIdentifier
    ;

queryHintClause
    : OPTION LPAREN queryHintItem (COMMA queryHintItem)* RPAREN
    ;

queryHintItem
    : RECOMPILE
    | MAXDOP number
    | OPTIMIZE FOR LPAREN queryHintContent* RPAREN
    | identifier queryHintContent*
    ;

queryHintContent
    : LPAREN queryHintContent* RPAREN
    | identifier
    | number
    | string
    | COMMA
    | DOT
    | EQ
    ;

topClause
    : TOP (LPAREN expression RPAREN | NUMBER_LITERAL) PERCENT_KEYWORD? (WITH TIES)?
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
    : relationPrimary tableOperator* joinRelation*
    ;

relationPrimary
    : multipartIdentifier FOR SYSTEM_TIME temporalTablePeriod tableHint? tableAlias #temporalTableName
    | multipartIdentifier LPAREN expressionList? RPAREN tableFunctionSchema? tableAlias #tableValuedFunction
    | multipartIdentifier tableHint? tableAlias                      #tableName
    | LPAREN VALUES valuesClause (COMMA valuesClause)* RPAREN tableAlias #valuesTable
    | LPAREN query RPAREN tableAlias                                 #aliasedQuery
    | LPAREN relation RPAREN tableAlias                              #aliasedRelation
    ;

temporalTablePeriod
    : AS OF expression
    | FROM expression TO expression
    | BETWEEN expression AND expression
    | CONTAINED IN LPAREN expression COMMA expression RPAREN
    | ALL
    ;

tableFunctionSchema
    : WITH LPAREN tableFunctionColumn (COMMA tableFunctionColumn)* RPAREN
    ;

tableFunctionColumn
    : identifier dataType string? (AS JSON)?
    ;

tableHint
    : WITH LPAREN tableHintItem (COMMA tableHintItem)* RPAREN
    ;

tableHintItem
    : identifier (LPAREN expressionList? RPAREN)?
    ;

joinRelation
    : joinType? JOIN relationPrimary joinCriteria?
    | applyType relationPrimary
    ;

joinType
    : INNER
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    | CROSS
    ;

applyType
    : CROSS APPLY
    | OUTER APPLY
    ;

joinCriteria
    : ON expression
    | USING LPAREN identifierList RPAREN
    ;

tableOperator
    : pivotClause
    | unpivotClause
    ;

pivotClause
    : PIVOT LPAREN expression FOR multipartIdentifier IN LPAREN pivotInItem (COMMA pivotInItem)* RPAREN RPAREN tableAlias
    ;

unpivotClause
    : UNPIVOT LPAREN multipartIdentifier FOR multipartIdentifier IN LPAREN identifierList RPAREN RPAREN tableAlias
    ;

pivotInItem
    : identifier
    | string
    | number
    ;

tableAlias
    : (AS? strictIdentifier (LPAREN identifierList RPAREN)?)?
    ;

whereClause
    : WHERE expression
    ;

groupByClause
    : GROUP BY groupByItem (COMMA groupByItem)* legacyGroupByModifier?
    ;

legacyGroupByModifier
    : WITH (ROLLUP | CUBE)
    ;

groupByItem
    : expression
    | ROLLUP LPAREN expressionList RPAREN
    | CUBE LPAREN expressionList RPAREN
    | GROUPING SETS LPAREN groupingSet (COMMA groupingSet)* RPAREN
    ;

groupingSet
    : LPAREN expressionList? RPAREN
    | expression
    ;

havingClause
    : HAVING expression
    ;

queryOrganization
    : (ORDER BY sortItem (COMMA sortItem)*)?
      (LIMIT expression)?
      (OFFSET expression ROWS?)?
      (FETCH (FIRST | NEXT) expression ROWS ONLY)?
      forOutputClause?
    ;

forOutputClause
    : FOR (JSON | XML) forOutputMode (COMMA forOutputOption)*
    ;

forOutputMode
    : identifier (LPAREN string RPAREN)?
    ;

forOutputOption
    : identifier
    | ROOT LPAREN string RPAREN
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
    | valueExpression COLLATE identifier                             #collateExpression
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
    | TRY_CAST LPAREN expression AS dataType RPAREN                  #tryCastExpr
    | CONVERT LPAREN dataType COMMA expression (COMMA expression)? RPAREN #convertExpr
    | TRY_CONVERT LPAREN dataType COMMA expression (COMMA expression)? RPAREN #tryConvertExpr
    | functionName LPAREN STAR RPAREN withinGroupClause? (OVER windowSpec)?             #functionCallStar
    | functionName LPAREN setQuantifier? expressionList RPAREN withinGroupClause? (OVER windowSpec)?  #functionCall
    | functionName LPAREN RPAREN withinGroupClause? (OVER windowSpec)?                  #functionCallEmpty
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

withinGroupClause
    : WITHIN GROUP LPAREN ORDER BY sortItem (COMMA sortItem)* RPAREN
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
      outputClause?
      (query | VALUES valuesClause (COMMA valuesClause)* | executeStatement)
    ;

valuesClause
    : LPAREN expressionList RPAREN
    ;

updateStatement
    : ctes? UPDATE topClause? multipartIdentifier tableAlias SET assignmentList outputClause? fromClause? whereClause?
    ;

deleteStatement
    : ctes? DELETE topClause? FROM multipartIdentifier tableAlias outputClause? whereClause?  #deleteSimple
    | ctes? DELETE topClause? multipartIdentifier outputClause? FROM relationList whereClause? #deleteFromJoin
    ;

outputClause
    : OUTPUT outputItemList (INTO multipartIdentifier (LPAREN identifierList RPAREN)?)?
    ;

outputItemList
    : outputItem (COMMA outputItem)*
    ;

outputItem
    : (expression | identifier DOT STAR | STAR) (AS? alias=identifier)?
    ;

mergeStatement
    : ctes? MERGE INTO? multipartIdentifier tableHint? tableAlias
      USING (multipartIdentifier tableAlias | LPAREN query RPAREN tableAlias)
      ON expression
      mergeClause+
      outputClause?
    ;

mergeClause
    : WHEN MATCHED (AND matchedCondition=expression)? THEN mergeMatchedAction
    | WHEN NOT MATCHED BY SOURCE (AND bySourceCondition=expression)? THEN mergeMatchedBySourceAction
    | WHEN NOT MATCHED (BY TARGET)? (AND notMatchedCondition=expression)? THEN mergeNotMatchedAction
    ;

mergeMatchedAction
    : UPDATE SET assignmentList whereClause?
    ;

mergeNotMatchedAction
    : INSERT (LPAREN identifierList RPAREN)? VALUES LPAREN expressionList RPAREN whereClause?
    ;

mergeMatchedBySourceAction
    : UPDATE SET assignmentList whereClause?
    | DELETE whereClause?
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
      indexOptionClause?
    ;

createStatisticsStatement
    : CREATE STATISTICS multipartIdentifier
      ON multipartIdentifier LPAREN identifierList RPAREN
      whereClause?
    ;

updateStatisticsStatement
    : UPDATE STATISTICS tableName=multipartIdentifier statsName=multipartIdentifier?
      (WITH updateStatisticsOptionList)?
    ;

dropStatisticsStatement
    : DROP STATISTICS multipartIdentifier
    ;

updateStatisticsOptionList
    : updateStatisticsOption (COMMA updateStatisticsOption)*
    ;

updateStatisticsOption
    : identifier (EQ (identifier | number | string))?
    ;

indexOptionClause
    : WITH LPAREN indexOptionList RPAREN
    ;

indexOptionList
    : indexOption (COMMA indexOption)*
    ;

indexOption
    : identifier (EQ indexOptionValue)?
    ;

indexOptionValue
    : identifier
    | ON
    | OFF
    | number
    | string
    ;

dropIndexStatement
    : DROP INDEX (IF EXISTS)? multipartIdentifier ON multipartIdentifier indexOptionClause?
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
    : CREATE EXTERNAL? TEMPORARY? TABLE (IF NOT EXISTS)? multipartIdentifier
      (LPAREN tableElementList RPAREN)?
      commentClause?
      externalTableOptionClause?
      (AS query)?
    | CREATE TEMPORARY? TABLE (IF NOT EXISTS)? target=multipartIdentifier LIKE source=multipartIdentifier
    ;

externalTableOptionClause
    : WITH LPAREN externalTableOptionContent* RPAREN
    ;

externalTableOptionContent
    : LPAREN externalTableOptionContent* RPAREN
    | identifier
    | number
    | string
    | COMMA
    | EQ
    ;

declareTableVariableStatement
    : DECLARE identifier TABLE LPAREN tableElementList RPAREN
    ;

declareScalarVariableStatement
    : DECLARE scalarVariableDeclaration (COMMA scalarVariableDeclaration)*
    ;

scalarVariableDeclaration
    : identifier AS? dataType (EQ expression)?
    ;

createSchemaStatement
    : CREATE SCHEMA identifier
    ;

databaseStatement
    : CREATE DATABASE identifier .*?
    | ALTER DATABASE identifier .+?
    | DROP DATABASE (IF EXISTS)? identifier
    ;

alterSchemaStatement
    : ALTER SCHEMA targetSchema=identifier TRANSFER objectScope? source=multipartIdentifier
    ;

typeStatement
    : CREATE TYPE multipartIdentifier FROM dataType (NULL | NOT NULL)?
    | CREATE TYPE multipartIdentifier AS TABLE LPAREN tableElementList RPAREN
    | DROP TYPE (IF EXISTS)? multipartIdentifier
    ;

createSynonymStatement
    : CREATE SYNONYM multipartIdentifier FOR multipartIdentifier
    ;

createProcedureStatement
    : CREATE (OR ALTER)? PROCEDURE multipartIdentifier .+?
    ;

createTriggerStatement
    : CREATE (OR ALTER)? TRIGGER multipartIdentifier ON multipartIdentifier .+?
    ;

createViewStatement
    : CREATE (OR (REPLACE | ALTER))? VIEW (IF NOT EXISTS)? multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      viewOptionClause?
      AS query
    ;

alterViewStatement
    : ALTER VIEW multipartIdentifier
      (LPAREN viewColumnList=identifierList RPAREN)?
      viewOptionClause?
      AS query
    ;

viewOptionClause
    : WITH identifier (COMMA identifier)*
    ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? multipartIdentifier (COMMA multipartIdentifier)*
    ;

dropViewStatement
    : DROP VIEW (IF EXISTS)? multipartIdentifier (COMMA multipartIdentifier)*
    ;

dropSchemaStatement
    : DROP SCHEMA (IF EXISTS)? identifier
    ;

dropSynonymStatement
    : DROP SYNONYM (IF EXISTS)? multipartIdentifier
    ;

dropProcedureStatement
    : DROP PROCEDURE (IF EXISTS)? multipartIdentifier
    ;

dropTriggerStatement
    : DROP TRIGGER (IF EXISTS)? multipartIdentifier
    ;

truncateTableStatement
    : TRUNCATE TABLE? multipartIdentifier truncateTableOption?
    ;

truncateTableOption
    : WITH LPAREN PARTITIONS LPAREN expressionList RPAREN RPAREN
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
    : SET optionName=identifier tableName=multipartIdentifier (ON | OFF) #setIdentityInsertStatement
    | SET identifier (ON | OFF | EQ expression)?                         #setOptionStatement
    ;

executeStatement
    : (EXEC | EXECUTE) .+?
    ;

transactionStatement
    : BEGIN (TRAN | TRANSACTION) identifier?
    | COMMIT (TRAN | TRANSACTION)? identifier?
    | ROLLBACK (TRAN | TRANSACTION)? identifier?
    | SAVE (TRAN | TRANSACTION) identifier
    ;

backupRestoreStatement
    : BACKUP (DATABASE | LOG) multipartIdentifier .+?
    | RESTORE DATABASE multipartIdentifier .+?
    ;

dbccStatement
    : DBCC CHECKTABLE LPAREN multipartIdentifier RPAREN .*?
    | DBCC CHECKDB LPAREN? (identifier | string)? RPAREN? .*?
    | DBCC .+?
    ;

scriptControlStatement
    : IF .+?
    | BEGIN TRY .+? END TRY BEGIN CATCH .+? END CATCH
    | PRINT .+?
    | THROW .+?
    | RAISERROR .+?
    | RETURN .*?
    ;

grantStatement
    : GRANT .+? ON objectScope? multipartIdentifier TO .+?
    ;

revokeStatement
    : REVOKE .+? ON objectScope? multipartIdentifier FROM .+?
    ;

objectScope
    : OBJECT DOUBLE_COLON
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
    | identifier AS expression PERSISTED?
    ;

columnConstraint
    : NOT NULL
    | NULL
    | COMMENT string
    | DEFAULT expression
    | IDENTITY LPAREN NUMBER_LITERAL COMMA NUMBER_LITERAL RPAREN
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
    | VARIABLE_IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | nonReservedKeyword
    ;

strictIdentifier
    : IDENTIFIER
    | VARIABLE_IDENTIFIER
    | BACKQUOTED_IDENTIFIER
    | nonReservedKeyword
    ;

nonReservedKeyword
    : ADD | APPLY | ASC | CAST | COLLATE | COLUMN | COMMENT | CONTAINED | DEFAULT
    | BACKUP | BEGIN | CHECKDB | CHECKTABLE | CLUSTERED | COMMIT | DATABASE | DBCC | DECLARE | DESCRIBE | DESC | DISABLE | DISK | END | EXEC | EXECUTE | EXISTS | EXTERNAL | FALSE
    | IDENTITY | INCLUDE | INDEX | INTERVAL | JSON | KEY | LIKE | LIMIT | LOG | MAXDOP | NOLOCK | NONCLUSTERED | NULL | OFF | OPTION | OPTIMIZE
    | FETCH | FIRST | FOR | GRANT | GROUPING | NEXT | OBJECT | OF | OFFSET | ONLY | OUTPUT | OVER | PARTITION | PARTITIONS | PERCENT_KEYWORD | REPLACE | RENAME | REVOKE | ROOT | ROLLUP | ROW | ROWS
    | AFTER | CATCH | CUBE | PERSISTED | PRIMARY | PRINT | PROCEDURE | RAISERROR | REBUILD | RECOMPILE | REORGANIZE | RESTORE | RETURN | ROLLBACK | SAVE | SCHEMA | SET | SETS | SHOW | SOURCE | STATISTICS | SYNONYM | SYSTEM_TIME | TABLE | TARGET | TEMPORARY | THROW | TIES | TO | TOP | TRAN | TRANSACTION | TRANSFER | TRIGGER | TRUE | TRY | TRY_CAST | TRY_CONVERT | TRUNCATE | TYPE | USE | VALUES | VIEW
    | CONVERT
    | UNIQUE | WITHIN | XML
    ;

number
    : NUMBER_LITERAL
    ;

string
    : STRING_LITERAL
    | DOUBLE_QUOTED_STRING
    ;
