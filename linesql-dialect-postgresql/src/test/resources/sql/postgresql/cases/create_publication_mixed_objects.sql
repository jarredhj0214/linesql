CREATE PUBLICATION app_events FOR TABLE ods.events WHERE (active),
    TABLES IN SCHEMA downstream, CURRENT_SCHEMA,
    TABLE ods.audit WHERE (created_at IS NOT NULL);
