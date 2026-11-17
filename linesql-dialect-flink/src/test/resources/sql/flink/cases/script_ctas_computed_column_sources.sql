CREATE TABLE mart.orders_summary (
  description STRING,
  cost AS price * quantity,
  price DECIMAL(10, 2)
) WITH ('connector'='kafka') AS
SELECT id, price, quantity FROM ods.orders WHERE active;
SELECT * FROM mart.orders_summary ORDER BY 2;
