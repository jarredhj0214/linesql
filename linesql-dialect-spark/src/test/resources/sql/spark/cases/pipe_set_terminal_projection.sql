SELECT price AS unit_price, qty, amount FROM ods.orders
|> SET amount = unit_price * qty
