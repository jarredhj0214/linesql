WITH delta AS (
    SELECT item_id, change_value FROM updates
)
MERGE INTO aggregates t USING delta d ON t.id = d.item_id
WHEN MATCHED THEN UPDATE SET amount = d.change_value;
