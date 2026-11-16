SELECT a.id
FROM ods.a a
/* outer comment
   /* inner comment */
   ; SELECT id FROM hidden_table;
*/
WHERE a.active = true;
