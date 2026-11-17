CREATE TABLE ods.events (event_id BIGINT, amount DECIMAL(10, 2)) WITH ('connector' = 'datagen');
SELECT emitted.*
FROM TO_CHANGELOG(input => TABLE ods.events, op => DESCRIPTOR(change_kind))
AS emitted(operation, raw_id, raw_amount)
WHERE emitted.raw_amount > 0;
