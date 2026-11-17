SELECT j.item_id, j."Display Name" AS display_name
FROM ods.events e,
     JSON_TABLE(e.payload, '$' COLUMNS (
         NESTED PATH '$.items[*]' COLUMNS (
             item_id NUMBER PATH '$.id',
             "Display Name" VARCHAR2(100) PATH '$.name'
         )
     )) j;
