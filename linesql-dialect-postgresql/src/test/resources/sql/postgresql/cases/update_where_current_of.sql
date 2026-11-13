UPDATE mart.jobs AS j
SET attempts = j.attempts + 1
WHERE CURRENT OF pending_jobs
RETURNING j.id;
