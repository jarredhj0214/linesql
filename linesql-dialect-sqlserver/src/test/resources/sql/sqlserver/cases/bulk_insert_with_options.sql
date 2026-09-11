bulk insert ods.orders_stage
from 's3://bucket/orders.csv'
with (
  firstrow = 2,
  fieldterminator = ',',
  rowterminator = '0x0a'
);
