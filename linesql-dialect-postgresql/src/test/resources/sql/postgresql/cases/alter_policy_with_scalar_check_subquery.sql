ALTER POLICY member_access ON mart.orders WITH CHECK (
    amount <= (
        SELECT max(l.max_amount)
        FROM security.limits l
        WHERE l.tenant_id = orders.tenant_id
    )
);
