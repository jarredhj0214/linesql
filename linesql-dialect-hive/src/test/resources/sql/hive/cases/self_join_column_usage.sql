SELECT e.id, m.name
FROM ods.employees e
LEFT JOIN ods.employees m ON e.manager_id = m.id;
