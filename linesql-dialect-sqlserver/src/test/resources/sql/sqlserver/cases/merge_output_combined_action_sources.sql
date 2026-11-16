MERGE dbo.orders AS t USING stage.orders AS s ON t.id = s.id
WHEN MATCHED THEN UPDATE SET amount = s.updated_amount
WHEN NOT MATCHED BY SOURCE THEN UPDATE SET inactive = 1
WHEN NOT MATCHED THEN INSERT (id, amount) VALUES (s.id, s.original_amount)
OUTPUT $action AS action_name, inserted.amount AS final_amount, deleted.amount AS old_amount;
