alter subscription sub_sales
set publication pub_sales, pub_orders
with (refresh = true);
