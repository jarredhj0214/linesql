create external data source lake_storage
with (
  location = 'abfss://warehouse@account.dfs.core.windows.net',
  credential = lake_credential
)
