CREATE TABLE mart.inbound_events (
    payload text,
    CONSTRAINT valid_document CHECK (payload IS JSON OBJECT WITH UNIQUE KEYS)
);
