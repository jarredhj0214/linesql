DECLARE VARIABLE command_text STRING DEFAULT 'SELECT id FROM ods.inactive';
SET VARIABLE command_text = concat('SELECT id ', 'FROM ods.unknown');
EXECUTE IMMEDIATE command_text;
SET VARIABLE command_text = 'SELECT id FROM ods.active';
EXECUTE IMMEDIATE session.command_text;
EXECUTE IMMEDIATE 'SELECT 1' INTO command_text;
EXECUTE IMMEDIATE command_text;
DROP TEMPORARY VARIABLE command_text;
EXECUTE IMMEDIATE command_text;
