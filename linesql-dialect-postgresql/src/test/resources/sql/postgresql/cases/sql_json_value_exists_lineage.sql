SELECT JSON_VALUE(e.payload, '$.amount' RETURNING numeric
                  DEFAULT e.fallback_amount ON EMPTY NULL ON ERROR) AS amount
FROM ods.events AS e
WHERE JSON_EXISTS(e.payload, '$.items[*] ? (@.id == $item_id)'
                  PASSING e.item_id AS item_id FALSE ON ERROR);
