select product_id, projected_sales
from mart.sales_forecast
model return all rows
  partition by (product_id)
  dimension by (year_id)
  measures (sales_amount projected_sales)
  rules upsert all (
    projected_sales[2026] = projected_sales[2025] * 1.10
  );
