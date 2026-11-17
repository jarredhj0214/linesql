INSERT INTO report.audit_event (marker, event_id, adjusted_total)
EXEC sys.sp_executesql
    @stmt = N'SELECT 7, e.id, e.total + e.tax
             FROM dbo.events e WHERE e.status = N''ready'' AND e.id >= @min',
    @params = N'@min int', @min = 10;
