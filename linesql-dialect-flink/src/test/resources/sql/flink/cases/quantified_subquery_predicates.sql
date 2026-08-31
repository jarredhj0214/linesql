select o.user_id, o.amount
from dwd.orders o
where o.amount > all (
  select r.limit_amount
  from dim.risk_limits r
  where r.user_id = o.user_id
)
and o.discount < any (
  select p.discount
  from dim.promotions p
);
