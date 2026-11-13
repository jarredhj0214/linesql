INSERT INTO dbo.jobs
OUTPUT inserted.id AS job_id
DEFAULT VALUES;
