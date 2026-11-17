CREATE TABLE ods.events (id BIGINT, amount BIGINT, is_valid BOOLEAN);
MERGE INTO mart.totals
USING ods.events AS q(event_id, new_amount, eligible)
ON mart.totals.id = q.event_id
WHEN MATCHED AND q.eligible THEN
    UPDATE SET amount = q.new_amount
WHEN NOT MATCHED THEN
    INSERT (id, amount) VALUES (q.event_id, q.new_amount);
