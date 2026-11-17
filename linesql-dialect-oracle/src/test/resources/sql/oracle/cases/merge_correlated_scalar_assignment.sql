MERGE INTO mart.orders t
USING ods.delta d
ON t.id = d.id
WHEN MATCHED THEN UPDATE SET latest_flag = (
    SELECT MAX(f.change_flag)
    FROM ods.flags f
    WHERE f.order_id = d.id
);
