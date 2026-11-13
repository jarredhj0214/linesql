DELETE FROM mart.jobs AS j
WHERE CURRENT OF completed_jobs
RETURNING j.id;
