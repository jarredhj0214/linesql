UPDATE t
SET state = s.state
FROM dbo.jobs AS t
INNER HASH JOIN staging.jobs_delta AS s ON t.id = s.id
WHERE s.ready = 1;
