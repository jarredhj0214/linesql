MERGE dbo.orders AS t USING stage.changed_orders AS s
ON t.id = s.id
WHEN MATCHED THEN UPDATE SET amount =
    (SELECT MAX(a.amount) FROM stage.adjustments a WHERE a.order_id = s.id)
OUTPUT inserted.amount AS new_amount;
