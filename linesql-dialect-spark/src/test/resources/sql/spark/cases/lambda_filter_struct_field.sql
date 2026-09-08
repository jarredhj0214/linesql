select filter(items, x -> x.status = 'PAID') as paid_items
from ods.orders
