USE [Raw];
GO
WITH source_rows AS (SELECT o.id FROM dbo.orders o WHERE o.active = 1)
SELECT id FROM source_rows;
EXEC sys.sp_executesql N'SELECT id FROM dbo.orders';
EXEC sys.sp_executesql N'USE [isolated]';
SELECT id FROM dbo.orders;
CREATE TABLE #scratch (id int);
SELECT id FROM #scratch;
USE [Mart];
INSERT INTO dbo.summary (id) SELECT id FROM [Raw].dbo.orders;
