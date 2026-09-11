alter publication pub_sales
set table only mart.users, sales.orders
with (publish = 'insert, update');
