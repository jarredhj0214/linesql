FROM ods.orders
|> SET amount = 0
|> SELECT *
|> WHERE amount = 0 AND id IS NOT NULL
|> SELECT amount
