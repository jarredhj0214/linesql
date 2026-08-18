SELECT u.id, p.channel
FROM ods_users u
JOIN dwd_orders o USING (id)
JOIN dwd_payments p USING (id);
