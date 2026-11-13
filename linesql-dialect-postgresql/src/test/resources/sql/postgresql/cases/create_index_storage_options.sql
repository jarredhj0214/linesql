CREATE UNIQUE INDEX ON ONLY mart.users USING btree
    ((lower(email)) COLLATE pg_catalog."C" pg_catalog.text_ops DESC NULLS LAST)
    INCLUDE (id) NULLS NOT DISTINCT
    WITH (fillfactor = 80) TABLESPACE fast_index
    WHERE active;
