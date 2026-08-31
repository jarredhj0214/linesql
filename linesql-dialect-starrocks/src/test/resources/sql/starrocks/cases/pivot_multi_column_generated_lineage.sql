select total_amount_north_app
from dwd.sales
pivot (
  sum(amount) as total_amount
  for (region, channel) in (('north', 'app'), ('south', 'web'))
);
