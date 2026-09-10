alter sequence if exists mart.seq_event_id
restart with 10000
owned by mart.audit_events.id;
