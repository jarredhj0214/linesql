CREATE PUBLICATION app_events FOR TABLE ONLY (ods.events) (id, account_id)
    WHERE (active AND tenant_id = 42),
    ods.audit * (id) WHERE (tenant_id > 0)
WITH (publish = 'insert, update');
