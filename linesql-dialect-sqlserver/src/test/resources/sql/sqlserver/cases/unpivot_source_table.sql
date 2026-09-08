select *
from mart.monthly_sales
unpivot (
    amount for month_name in ([jan_amount], [feb_amount])
) u;
