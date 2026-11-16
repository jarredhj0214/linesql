EXEC sys.sp_executesql
    N'INSERT INTO dbo.audit (event_id) SELECT id FROM dbo.events WHERE active = 1;';
