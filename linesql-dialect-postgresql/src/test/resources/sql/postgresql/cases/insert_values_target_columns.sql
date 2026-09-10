insert into mart.audit_events(id, event_type, created_at)
values (1001, 'LOGIN', now()),
       (1002, 'LOGOUT', now());
