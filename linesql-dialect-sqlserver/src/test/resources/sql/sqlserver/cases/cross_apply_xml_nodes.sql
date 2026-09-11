select o.id,
       x.n.value('(sku/text())[1]', 'varchar(40)') as sku
from ods.orders o
cross apply o.payload.nodes('/order/items/item') as x(n)
where o.status = 'PAID';
