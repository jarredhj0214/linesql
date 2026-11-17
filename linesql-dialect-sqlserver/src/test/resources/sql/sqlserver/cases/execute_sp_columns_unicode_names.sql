EXEC sys.sp_columns
    @table_owner = N'ops.schema',
    @table_qualifier = N'warehouse',
    @table_name = N'order.history',
    @column_name = N'order''id';
