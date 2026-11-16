MERGE dbo.orders AS t USING stage.orders AS s ON t.id = s.id
WHEN MATCHED THEN DELETE
OUTPUT inserted.id AS new_id, deleted.id AS old_id;
