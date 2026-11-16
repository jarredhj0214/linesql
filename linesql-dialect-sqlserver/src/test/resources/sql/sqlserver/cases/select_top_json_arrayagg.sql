SELECT TOP (5) e.segment,
       JSON_ARRAYAGG(e.payload ORDER BY e.created_at DESC ABSENT ON NULL RETURNING JSON) AS items
FROM dbo.events e
GROUP BY e.segment;
