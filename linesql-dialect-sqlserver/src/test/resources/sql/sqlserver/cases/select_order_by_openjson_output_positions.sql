SELECT j.*, d.created_at
FROM ods.documents AS d
CROSS APPLY OPENJSON(d.payload)
    WITH (event_id bigint '$.id', amount decimal(10, 2) '$.amount') AS j
ORDER BY 2, 3;
