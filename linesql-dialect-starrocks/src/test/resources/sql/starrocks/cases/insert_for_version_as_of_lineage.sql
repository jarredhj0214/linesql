insert into iceberg.sales.order_branch for version as of 'test-branch'
select order_id, amount from dwd.orders
