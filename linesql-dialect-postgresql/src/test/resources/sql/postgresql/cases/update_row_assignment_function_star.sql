UPDATE ONLY mart.inventory i
SET (lot, detail, previous_status) = (
    SELECT v.*, i.status
    FROM JSONB_EACH_TEXT(i.stock_detail) AS v(lot_number, lot_detail)
    LIMIT 1
);
