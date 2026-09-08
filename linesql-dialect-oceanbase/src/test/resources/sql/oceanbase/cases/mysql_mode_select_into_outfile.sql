SELECT id, status
INTO OUTFILE '/tmp/orders.csv'
FIELDS TERMINATED BY ','
FROM app.orders
WHERE status = 'PAID';
