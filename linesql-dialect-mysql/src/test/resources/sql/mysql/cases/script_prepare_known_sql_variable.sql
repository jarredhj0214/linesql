SET @source_sql = 'SELECT o.id FROM ods.orders o WHERE o.status = ''paid''';
SET @query_sql := @source_sql;
PREPARE read_orders FROM @QUERY_SQL;
SELECT command_text INTO @query_sql FROM config.runtime;
PREPARE runtime_orders FROM @query_sql;
SET @query_sql = 'SELECT a.id FROM ods.archived a';
SET @query_sql = CONCAT('SELECT id FROM ', @runtime_table);
PREPARE runtime_table_stmt FROM @query_sql;
