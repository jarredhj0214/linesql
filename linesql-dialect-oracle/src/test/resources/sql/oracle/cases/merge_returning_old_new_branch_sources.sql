MERGE INTO mart.orders t
USING staging.order_changes s ON (t.id = s.id)
WHEN MATCHED THEN UPDATE SET t.amount = s.update_amount
WHEN NOT MATCHED THEN INSERT (id, amount, note) VALUES (s.id, s.insert_amount, s.note)
RETURNING OLD t.amount, NEW t.amount, NEW t.note INTO :old_amount, :new_amount, :new_note;
