FROM ods.orders
|> SET total = price * quantity
|> AS o
|> WHERE o.total > 0
|> SELECT o.id, o.total
