UPDATE mart.orders o
SET o.amount = s.amount
FROM staging.amount_updates s
WHERE o.id = s.order_id
RETURNING OLD o.amount, NEW o.amount INTO :old_amount, :new_amount;
