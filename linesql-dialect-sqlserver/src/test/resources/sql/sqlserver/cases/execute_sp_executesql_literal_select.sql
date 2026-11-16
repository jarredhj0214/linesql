EXECUTE [sys].[sp_executesql]
    @stmt = N'SELECT e.id FROM dbo.events e WHERE e.status = ''OPEN'' AND e.id = @id',
    @params = N'@id int', @id = 42;
