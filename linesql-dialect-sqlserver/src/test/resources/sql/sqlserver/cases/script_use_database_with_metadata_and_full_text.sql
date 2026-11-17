USE [SearchDB];
SELECT ft.[KEY], ft.RANK FROM CONTAINSTABLE(dbo.Documents, body, N'demo') ft;
DBCC CHECKIDENT ('dbo.Documents', NORESEED);
EXEC sp_help N'dbo.Documents';
GO
CREATE PROCEDURE dbo.refresh_summary AS
INSERT INTO dbo.summary (id) SELECT id FROM dbo.Documents;
GO
