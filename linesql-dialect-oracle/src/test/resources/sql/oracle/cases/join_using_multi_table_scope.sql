SELECT u.id, p.channel
FROM ods.users u
JOIN dwd.orders o USING (id)
JOIN dwd.payments p USING (id);
