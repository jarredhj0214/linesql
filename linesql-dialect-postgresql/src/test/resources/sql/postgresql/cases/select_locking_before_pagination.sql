SELECT j.id FROM mart.jobs AS j
WHERE j.status = 'queued'
ORDER BY j.id
FOR UPDATE OF j SKIP LOCKED
OFFSET 10 LIMIT 5;
