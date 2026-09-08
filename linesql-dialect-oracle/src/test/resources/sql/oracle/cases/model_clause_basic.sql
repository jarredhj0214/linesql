select product_id, year_id, sales_amount
from mart.sales_forecast
model return updated rows
  partition by (product_id)
  dimension by (year_id)
  measures (sales_amount)
  rules (
    sales_amount[2026] = sales_amount[2025] * 1.10
  );
