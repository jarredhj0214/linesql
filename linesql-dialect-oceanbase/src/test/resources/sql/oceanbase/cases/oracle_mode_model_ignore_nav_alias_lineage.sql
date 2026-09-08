select product_id, projected_sales
from mart.sales_forecast
model ignore nav
  partition by (product_id)
  dimension by (year_id)
  measures (sales_amount projected_sales)
  rules (
    projected_sales[2026] = projected_sales[2025] * 1.05
  );
