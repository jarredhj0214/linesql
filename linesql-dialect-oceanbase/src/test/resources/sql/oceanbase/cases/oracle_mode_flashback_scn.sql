SELECT id, status
FROM ods.orders AS OF SCN 1582807800000000
WHERE status = 'PAID';
