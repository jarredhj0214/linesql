CREATE FOREIGN TABLE ext.enriched_events (source_note TEXT)
INHERITS (mart.events, mart.audit_fields)
SERVER archive_fdw;
