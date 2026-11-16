SELECT id
FROM ods.left_events l RIGHT OUTER JOIN ods.right_events r USING (id)
ORDER BY id;
