DECLARE VARIABLE command_text STRING DEFAULT
    'SELECT e.id, e.amount FROM ods.events e WHERE e.active';
EXECUTE IMMEDIATE system.session.command_text;
SET VARIABLE session.command_text = 'INSERT INTO mart.daily (id) SELECT id FROM ods.sales';
EXECUTE IMMEDIATE command_text;
