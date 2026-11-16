SELECT id, l.kind, r.amount
FROM ods.left_events l FULL OUTER JOIN ods.right_events r USING (id)
WHERE id > 0;
