DECLARE
    v_batch NUMBER := 1;
BEGIN
    BEGIN
        INSERT INTO mart.audit (id)
        SELECT id FROM ods.orders WHERE status = 'PAID';
    END;
    NULL;
END;
