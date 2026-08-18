SELECT e.id, m.name
FROM employees e
LEFT JOIN employees m ON e.manager_id = m.id;
