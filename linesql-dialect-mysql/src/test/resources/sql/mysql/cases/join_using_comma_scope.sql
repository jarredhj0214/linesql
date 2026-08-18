SELECT b.id
FROM app.audit_log a, app.users b
JOIN app.orders o USING (id);
