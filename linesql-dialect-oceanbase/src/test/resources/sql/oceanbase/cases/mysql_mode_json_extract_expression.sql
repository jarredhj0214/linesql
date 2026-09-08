SELECT payload->>'$.user.id' AS user_id,
       payload->'$.tags' AS tags
FROM app.events
WHERE payload->>'$.type' = 'click';
