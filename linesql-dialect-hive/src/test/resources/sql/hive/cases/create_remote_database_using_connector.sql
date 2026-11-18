CREATE REMOTE DATABASE IF NOT EXISTS upstream COMMENT 'linked schema'
USING reports_jdbc WITH DBPROPERTIES ('connector.remoteDbName' = 'reports')
