DELETE FROM ONLY (mart.events) AS e
USING staging.expired_events AS x
WHERE e.id = x.id
RETURNING e.id;
