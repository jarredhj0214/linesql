select id
from ods.orders
where status = 'NEW'
for update of status wait 5;
