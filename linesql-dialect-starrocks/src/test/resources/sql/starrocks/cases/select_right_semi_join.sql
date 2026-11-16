SELECT target.name
FROM ods.subscriptions matches RIGHT SEMI JOIN ods.customers target
ON matches.customer_id = target.id;
