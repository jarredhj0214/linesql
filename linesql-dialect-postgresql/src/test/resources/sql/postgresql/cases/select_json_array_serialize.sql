SELECT JSON_ARRAY(e.id, JSON(e.payload WITH UNIQUE KEYS) NULL ON NULL RETURNING jsonb) AS items,
       JSON_SERIALIZE(e.payload FORMAT JSON ENCODING UTF8 RETURNING bytea) AS bytes,
       json_object(e.field_names, e.field_values) AS legacy_object
FROM ods.events AS e;
