MERGE INTO mart.totals AS t
USING (
    SELECT e.id, e.amount, e.is_valid
    FROM ods.events AS e
    WHERE e.is_current = TRUE
) AS q(event_id, new_amount, eligible)
ON t.id = q.event_id
WHEN MATCHED AND q.eligible = TRUE THEN
    UPDATE SET amount = t.amount + q.new_amount
WHEN NOT MATCHED THEN
    INSERT (id, amount) VALUES (q.event_id, q.new_amount)
