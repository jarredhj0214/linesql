SELECT id, events.name, (
    SELECT MAX(f.flag) FROM archive.events f WHERE f.user_id = events.id
) AS flag
FROM live.accounts events;
