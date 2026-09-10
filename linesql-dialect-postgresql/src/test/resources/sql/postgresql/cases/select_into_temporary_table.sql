select o.user_id, sum(o.amount) as total_amount
into temporary table tmp_user_amounts
from sales.orders o
group by o.user_id;
