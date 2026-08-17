SELECT u.id, o.amount
FROM ods_users u
JOIN dwd_orders o USING (id);
