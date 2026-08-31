select
  convert(name using utf8mb4) as utf8_name,
  convert(amount, decimal(10,2)) as amount_decimal
from app.orders;
