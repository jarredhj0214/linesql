FROM ods.users
|> WHERE name ILIKE 'a%'
|> SELECT id;
