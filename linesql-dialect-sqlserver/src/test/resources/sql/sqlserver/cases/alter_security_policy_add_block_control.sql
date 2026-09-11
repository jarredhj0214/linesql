ALTER SECURITY POLICY sec.sales_policy
ADD BLOCK PREDICATE sec.fn_sales_block(tenant_id) ON dbo.orders AFTER INSERT
WITH (STATE = ON);
