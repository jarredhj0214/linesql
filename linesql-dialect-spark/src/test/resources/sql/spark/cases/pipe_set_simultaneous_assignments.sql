FROM ods.orders o
|> SET price = qty, qty = price
|> SELECT o.price AS units, o.qty AS unit_price
