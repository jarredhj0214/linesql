SELECT events.id, j.last_flag
FROM live.accounts events
CROSS JOIN LATERAL (
    SELECT MAX(f.flag) AS last_flag FROM archive.events f
    WHERE f.user_id = events.id
) j;
