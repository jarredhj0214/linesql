SELECT [prod].[live].[orders].[id] AS live_id, history.id AS history_id
FROM [prod].[live].[orders]
JOIN [prod].[archive].[orders] AS history ON [prod].[live].[orders].[link_id] = history.link_id
WHERE [prod].[live].[orders].[status] = 1;
