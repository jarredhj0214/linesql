SELECT e.id
FROM ods.events AS e
WHERE e.actor_id = ANY (
    SELECT p.actor_id FROM auth.permissions AS p WHERE p.active
);
