MERGE INTO mart.users t
USING (
  SELECT id, name, updated_at
  FROM staging.users_delta
  WHERE dt = '2026-09-11'
) s
ON t.id = s.id
WHEN MATCHED THEN UPDATE SET name = s.name
WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id, s.name)
