UPDATE dbo.orders
SET amount = a.amount
OUTPUT deleted.amount INTO audit.changes (old_amount)
OUTPUT inserted.amount AS new_amount, deleted.id AS order_id
FROM staging.adjustments a
WHERE dbo.orders.id = a.order_id;
