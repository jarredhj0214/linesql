PREPARE bump_job (bigint, integer) AS
UPDATE mart.jobs AS j
SET attempts = j.attempts + $2
WHERE j.id = $1
RETURNING j.id;
