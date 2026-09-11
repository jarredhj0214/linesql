select ft.rank
from freetexttable(dbo.documents, body, 'data governance') ft;
