CREATE FOREIGN TABLE ext.events_other
PARTITION OF mart.events (event_id WITH OPTIONS NOT NULL)
DEFAULT
SERVER archive_fdw;
