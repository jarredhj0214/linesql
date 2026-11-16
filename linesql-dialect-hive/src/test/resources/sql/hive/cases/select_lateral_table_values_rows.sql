SELECT v.currency, v.amount
FROM ods.finance t
LATERAL TABLE(VALUES (t.current_code, t.current_amount), (t.previous_code, t.previous_amount))
AS v(currency, amount)
WHERE v.amount > 0;
