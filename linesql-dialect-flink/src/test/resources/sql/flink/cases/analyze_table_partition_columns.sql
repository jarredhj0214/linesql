ANALYZE TABLE Orders PARTITION(sold_year='2022', sold_month, sold_day)
COMPUTE STATISTICS FOR COLUMNS amount, product
