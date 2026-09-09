select
  dateadd(day, 7, o.created_at) as next_week_at,
  datediff(month, o.start_at, o.end_at) as active_months,
  datepart(year, o.created_at) as created_year
from sales.orders o;
