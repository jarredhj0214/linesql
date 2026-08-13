from ods.orders
|> aggregate count(order_id) as order_cnt group by user_id
|> select user_id, order_cnt
