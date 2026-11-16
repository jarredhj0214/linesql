CREATE FUNCTION mart.extension_version()
RETURNS text
AS 'module_pathname', 'read_version'
LANGUAGE c STRICT IMMUTABLE COST 1;
