SELECT b.id
FROM ods_audit_log a, ods_users b
JOIN dwd_orders o USING (id);
