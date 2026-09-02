SELECT id
FROM dwd.events
WHERE JSON_EXISTS(payload, 'strict $.vin' TRUE ON ERROR)
