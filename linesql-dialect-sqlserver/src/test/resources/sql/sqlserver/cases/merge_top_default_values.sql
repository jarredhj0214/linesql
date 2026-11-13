MERGE TOP (10) PERCENT INTO dbo.jobs AS j
USING staging.jobs_delta AS d ON j.id = d.id
WHEN NOT MATCHED BY TARGET AND d.ready = 1 THEN INSERT DEFAULT VALUES
OUTPUT inserted.id AS job_id
OPTION (MAXDOP 1);
