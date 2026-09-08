SELECT id, amount
FROM app.orders
WHERE dt = current_date
INTO OUTFILE '/tmp/orders_export.csv'
FIELDS TERMINATED BY ','
LINES TERMINATED BY '\n';
