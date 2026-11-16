CREATE FUNCTION mart.active_names(IN p_min bigint = 1)
RETURNS TABLE (id bigint, name text)
LANGUAGE sql STABLE
AS $sql$ SELECT id, name FROM ods.users WHERE id >= p_min; $sql$;
