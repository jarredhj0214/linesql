SELECT c.[id]
FROM dbo.[customers] AS c
/* outer
    /* inner */
    ; SELECT id FROM hidden_table;
*/
WHERE c.active = 1;
