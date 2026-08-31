alter table mart.orders add rollup r_order_user(order_id, user_id) from orders properties("timeout" = "3600")
