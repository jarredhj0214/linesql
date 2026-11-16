SELECT pair.*
FROM ods.events AS e,
LATERAL stack(2, e.primary_code, e.primary_value, e.backup_code, e.backup_value) AS pair(code, amount);
