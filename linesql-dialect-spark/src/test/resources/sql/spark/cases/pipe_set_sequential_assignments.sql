FROM ods.orders o
|> SET price = qty, qty = price
|> SELECT price AS updated_price, qty AS updated_qty, o.price AS original_price, o.qty AS original_qty
