SELECT b.id
FROM ods.audit_log a, ods.users b
JOIN dwd.orders o USING (id);
