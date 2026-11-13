DELETE FROM dbo.jobs
OUTPUT deleted.id AS job_id
WHERE CURRENT OF GLOBAL completed_jobs
OPTION (MAXDOP 1);
