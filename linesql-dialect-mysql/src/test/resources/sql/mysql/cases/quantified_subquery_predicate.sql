select o.id
from app.orders o
where o.amount > all (
  select r.limit_amount
  from app.region_limits r
  where r.region = o.region
);
