create sequence if not exists mart.seq_event_id
as bigint
start with 1
increment by 1
cache 100
no cycle;
