INSERT INTO mart.finance_items (currency, amount)
SELECT v.col1, v.col2
FROM ods.finance t, LATERAL TABLE(VALUES (t.current_code, t.current_amount)) v;
