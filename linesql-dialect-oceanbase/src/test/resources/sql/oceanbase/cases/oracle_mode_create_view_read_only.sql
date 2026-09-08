CREATE VIEW mart.v_active_users AS
SELECT id AS user_id, name
FROM app.users
WHERE status = 'ACTIVE'
WITH READ ONLY;
