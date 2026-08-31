alter table mart.orders add rollup r_user(user_id, amount) from orders, r_dt(dt, amount) properties("timeout" = "3600")
