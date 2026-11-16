INSERT INTO audit.doc_items (item_code, item_position, doc_id)
SELECT x.*, d.id
FROM raw.docs d
CROSS JOIN XMLTABLE('/doc/item' PASSING d.payload::xml
    COLUMNS code text, position FOR ORDINALITY
) AS x;
