alter table mart.orders
add column net_amount decimal(18,2) sum default "0"
after amount
to rollup_orders
properties ("timeout" = "3600");
