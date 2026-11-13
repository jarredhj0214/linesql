UPDATE dbo.jobs
SET attempts += 1
OUTPUT inserted.attempts AS new_attempts
WHERE CURRENT OF @pending_jobs;
