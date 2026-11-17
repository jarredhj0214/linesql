MERGE INTO mart.events
USING ods.events
ON mart.events.id = ods.events.id
WHEN MATCHED THEN
    UPDATE SET amount = ods.events.amount;
