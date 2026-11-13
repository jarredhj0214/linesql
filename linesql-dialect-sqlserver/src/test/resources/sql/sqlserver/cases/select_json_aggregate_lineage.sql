SELECT p.owner_id,
       JSON_ARRAYAGG(p.value ORDER BY p.updated_at DESC NULL ON NULL RETURNING JSON) AS values_json,
       JSON_OBJECTAGG(p.name : p.value ABSENT ON NULL) AS properties_json
FROM dbo.properties AS p
GROUP BY p.owner_id;
