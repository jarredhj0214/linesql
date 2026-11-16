SELECT o.id
FROM ods.orders o
WHERE o.note = q'[It's paid; keep /* this */]' OR o.note = nq'!O'Brien; done!'
   OR o.note = q'🔥O'Brien;🔥' OR o.note = N'已支付';
