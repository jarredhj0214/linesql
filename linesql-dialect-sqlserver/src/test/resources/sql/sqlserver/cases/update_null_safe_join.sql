UPDATE t
SET status = s.status
FROM dbo.jobs AS t
JOIN staging.jobs_delta AS s ON t.external_id IS NOT DISTINCT FROM s.external_id
WHERE t.status IS DISTINCT FROM s.status;
