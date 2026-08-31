select
  interval(score, 60, 70, 80, 90) as score_bucket,
  field(status, 'NEW', 'PAID', 'DONE') as status_rank,
  elt(priority, 'LOW', 'MEDIUM', 'HIGH') as priority_name
from app.orders
where field(region, 'CN', 'EU', 'US') > 0
