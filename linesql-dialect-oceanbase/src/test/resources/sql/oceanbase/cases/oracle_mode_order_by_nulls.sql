SELECT id, amount
FROM ods.orders
ORDER BY amount DESC NULLS LAST, id ASC NULLS FIRST;
