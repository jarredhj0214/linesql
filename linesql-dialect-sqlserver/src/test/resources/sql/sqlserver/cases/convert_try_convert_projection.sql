select
  convert(decimal(18, 2), o.amount_text) as amount,
  try_convert(date, o.created_text, 120) as created_date
from dbo.orders o;
