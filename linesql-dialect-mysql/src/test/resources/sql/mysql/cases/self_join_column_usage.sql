SELECT e.id, m.name
FROM app.employees e
LEFT JOIN app.employees m ON e.manager_id = m.id;
