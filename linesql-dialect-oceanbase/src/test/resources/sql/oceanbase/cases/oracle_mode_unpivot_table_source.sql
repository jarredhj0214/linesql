select region, amount
from sales.monthly
unpivot (
  amount for region in (jan_amount as 'JAN', feb_amount as 'FEB')
)
