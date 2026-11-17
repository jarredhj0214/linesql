DELETE FROM [prod].[live].[orders]
OUTPUT deleted.id AS removed_id
FROM [prod].[live].[orders]
JOIN [prod].[archive].[orders] AS historical ON [prod].[live].[orders].[id] = historical.id
WHERE historical.purge_requested = 1;
