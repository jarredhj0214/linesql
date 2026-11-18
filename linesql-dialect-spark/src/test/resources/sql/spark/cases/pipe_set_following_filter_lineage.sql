FROM ods.orders
|> WHERE qty > 0
|> SET amount = price * qty
|> WHERE amount > 100
|> SELECT amount AS total
