CREATE /* imported */ FOREIGN
TABLE ext.orders (id BIGINT)
SERVER report_fdw OPTIONS (schema_name 'sales', table_name 'orders');
