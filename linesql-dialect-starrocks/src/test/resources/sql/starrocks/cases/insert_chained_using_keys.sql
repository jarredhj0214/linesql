INSERT INTO mart.merged_events (event_id, status)
SELECT id, c.state
FROM (ods.left_events l FULL OUTER JOIN ods.right_events r USING (id))
LEFT JOIN ods.event_state c USING (id);
