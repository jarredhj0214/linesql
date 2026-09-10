create publication pub_sales
for table only mart.users, sales.orders
with (publish = 'insert, update, delete');
