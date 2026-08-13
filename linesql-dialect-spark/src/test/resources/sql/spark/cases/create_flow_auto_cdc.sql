create flow mart.user_cdc as
auto cdc into mart.users
from ods.user_changes
keys (id)
