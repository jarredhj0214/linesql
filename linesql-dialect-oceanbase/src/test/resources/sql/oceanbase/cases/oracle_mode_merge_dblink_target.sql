MERGE INTO ods.remote_orders@remote_dw t
USING ods.orders s
ON (t.id = s.id)
WHEN MATCHED THEN UPDATE SET t.status = s.status
WHEN NOT MATCHED THEN INSERT (id, status) VALUES (s.id, s.status);
