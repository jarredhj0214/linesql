select id, amount
from mart.orders
where amount > 0
for no key update nowait;
