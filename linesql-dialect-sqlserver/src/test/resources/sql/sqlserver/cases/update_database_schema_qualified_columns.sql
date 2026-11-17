UPDATE [prod].[live].[orders]
SET status = [prod].[live].[orders].[status] + history.status
FROM [prod].[live].[orders]
JOIN [prod].[archive].[orders] AS history ON [prod].[live].[orders].[id] = history.id
WHERE [prod].[live].[orders].[active] = 1;
