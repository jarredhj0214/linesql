SELECT q.observed_at FROM (SELECT 0, e.observed_at FROM ods.events e) AS q(event_type, observed_at) WHERE q.observed_at IS NOT NULL;
