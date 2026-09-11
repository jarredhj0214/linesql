IMPORT FOREIGN SCHEMA remote_sales
EXCEPT (audit_log)
FROM SERVER sales_fdw
INTO ext;
