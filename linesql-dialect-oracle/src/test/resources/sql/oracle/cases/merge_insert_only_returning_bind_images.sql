MERGE INTO mart.orders t
USING staging.order_changes s ON (t.id = s.id)
WHEN NOT MATCHED THEN INSERT (id) VALUES (s.id)
RETURNING OLD t.id, NEW t.id INTO :previous_id, :inserted_id;
