select ft.[key], ft.rank
from containstable(dbo.products, (title, description), '"governance*"') as ft;
