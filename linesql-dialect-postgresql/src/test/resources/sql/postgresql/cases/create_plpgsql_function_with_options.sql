CREATE OR REPLACE FUNCTION mart.is_active(IN p_id bigint DEFAULT 0)
RETURNS boolean
AS $routine$
BEGIN
    PERFORM 'CREATE PUBLICATION ghost';
    RETURN p_id > 0;
END;
$routine$
LANGUAGE plpgsql STABLE RETURNS NULL ON NULL INPUT PARALLEL SAFE
SET search_path TO pg_catalog, mart;
