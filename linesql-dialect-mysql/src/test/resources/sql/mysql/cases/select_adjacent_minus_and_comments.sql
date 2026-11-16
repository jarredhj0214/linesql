# Keep this semicolon inside the comment;
SELECT e.amount--e.discount AS net
FROM ods.sales e -- keep this semicolon too;
WHERE e.active = 1;
