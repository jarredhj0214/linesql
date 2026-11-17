INSERT INTO audit.summary(observed_at)
WITH q(event_type, observed_at) AS (SELECT 0, e.observed_at FROM ods.events e)
SELECT q.observed_at FROM q;
