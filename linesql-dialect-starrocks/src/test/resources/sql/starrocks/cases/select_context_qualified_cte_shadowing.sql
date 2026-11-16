WITH events AS (SELECT event_number AS id FROM events)
SELECT q.id AS cte_id, p.id AS physical_id
FROM events q JOIN ods.events p ON q.id = p.id;
