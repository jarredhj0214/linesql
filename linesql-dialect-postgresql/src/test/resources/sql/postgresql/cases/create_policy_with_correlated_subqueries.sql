CREATE POLICY member_access ON mart.orders
USING (EXISTS (
    SELECT 1 FROM security.members m
    WHERE m.user_id = orders.user_id AND m.active
))
WITH CHECK (account_id IN (
    SELECT a.id FROM security.accounts a WHERE a.enabled
));
