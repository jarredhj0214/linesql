restore snapshot catalog_backup
from test_repo
external catalog hive_catalog as hive_catalog_new
properties ("backup_timestamp" = "2024-12-09-10-52-10-940")
