WITH orders AS (SELECT net AS amount, extra AS tax FROM ods.sales)
FROM orders
|> SET amount = amount + tax
|> SET amount = amount * 2
|> SELECT amount AS total
|> WHERE total > 100
|> SELECT total
