select order_id, user_id, amount
from dwd.orders
where amount > 0
order by rowtime desc
fetch first 100 rows only;
