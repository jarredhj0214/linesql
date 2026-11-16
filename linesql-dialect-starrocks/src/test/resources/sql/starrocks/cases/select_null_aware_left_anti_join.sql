SELECT target.id
FROM ods.customers target NULL AWARE LEFT ANTI JOIN ods.blocked_customers blocked
ON target.id = blocked.customer_id;
