CREATE OR ALTER PROC dbo.load_events AS
BEGIN
    INSERT INTO dbo.audit (id) SELECT id FROM dbo.events;
    SELECT 'literal
GO
kept' AS message;
END
GO 2 -- repeat the batch
SELECT id FROM dbo.audit
GO
