IMPORT FOREIGN SCHEMA remote_sales
LIMIT TO (orders, customers)
FROM SERVER sales_fdw
INTO ext
OPTIONS (import_default 'true');
