INSERT INTO audit.summary(event_type, observed_at)
WITH q(event_type, observed_at) AS (SELECT 0, e.observed_at FROM ods.events e)
SELECT * FROM q;
