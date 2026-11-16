EXECUTE STATEMENT SET BEGIN
    INSERT INTO mart.audit (bucket, net_amount)
    SELECT 1, o.amount - o.discount FROM ods.orders o WHERE o.active = TRUE;
    INSERT INTO mart.refunds (id, adjusted_amount)
    SELECT r.id, COALESCE(r.credit, r.amount) FROM ods.refunds r;
END;
