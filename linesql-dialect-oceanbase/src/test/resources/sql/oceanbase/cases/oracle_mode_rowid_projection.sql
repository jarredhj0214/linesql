select rowid as rid, id
from ods.orders
where status = 'NEW'
