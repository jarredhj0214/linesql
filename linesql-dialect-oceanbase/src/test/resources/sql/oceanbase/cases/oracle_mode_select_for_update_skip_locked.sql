select id, status
from ods.orders
where status = 'PENDING'
for update of status skip locked;
