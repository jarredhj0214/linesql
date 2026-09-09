set @max_order_id = (select max(id) from dbo.orders where status = 'PAID');
