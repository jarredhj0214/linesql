EXECUTE IMMEDIATE 'INSERT INTO mart.active_users (id)
SELECT id FROM ods.users WHERE state = \'ACTIVE\'';
