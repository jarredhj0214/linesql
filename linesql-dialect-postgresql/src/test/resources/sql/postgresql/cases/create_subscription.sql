create subscription sub_sales
connection 'host=127.0.0.1 port=5432 dbname=warehouse'
publication pub_sales
with (copy_data = false);
