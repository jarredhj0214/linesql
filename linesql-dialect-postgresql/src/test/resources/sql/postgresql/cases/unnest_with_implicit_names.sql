SELECT unnest.unnest AS element, unnest.ordinality AS position_number
FROM ods.events e CROSS JOIN UNNEST(e.tags) WITH ORDINALITY;
