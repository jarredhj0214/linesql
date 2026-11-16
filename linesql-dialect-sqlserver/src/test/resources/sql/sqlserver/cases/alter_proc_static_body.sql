ALTER PROC dbo.rollup AS
BEGIN
    /* Mention FROM ignored_table and INSERT INTO ignored_sink. */
    INSERT INTO mart.rollup SELECT id FROM ods.orders;
    SELECT 'FROM ignored_string' AS label FROM ods.customers;
END;
