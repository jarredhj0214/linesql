CREATE SECURITY POLICY sec.sales_policy
ADD FILTER PREDICATE sec.fn_sales_filter(tenant_id) ON dbo.orders
WITH (STATE = ON);
