SELECT item.col
FROM ods.events AS e,
LATERAL explode(array(e.primary_code, e.backup_code)) AS item;
