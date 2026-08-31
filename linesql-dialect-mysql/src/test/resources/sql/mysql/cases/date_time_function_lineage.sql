select
  adddate(created_at, interval grace_days day) as grace_deadline,
  subdate(expired_at, interval retry_hours hour) as retry_start_at,
  period_diff(close_period, open_period) as active_months,
  makedate(year_no, day_of_year) as year_day
from app.subscriptions
where last_day(billing_date) >= current_date

