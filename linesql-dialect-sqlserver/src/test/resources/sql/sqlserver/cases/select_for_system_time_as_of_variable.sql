SELECT e.id FROM dbo.employees FOR SYSTEM_TIME AS OF @version_time AS e WHERE e.active = 1;
