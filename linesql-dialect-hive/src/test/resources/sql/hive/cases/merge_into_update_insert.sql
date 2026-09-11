MERGE INTO mart.users t
USING staging.users_delta s
ON t.id = s.id
WHEN MATCHED AND s.op = 'U' THEN UPDATE SET name = s.name, updated_at = s.updated_at
WHEN NOT MATCHED THEN INSERT (id, name, updated_at) VALUES (s.id, s.name, s.updated_at)
