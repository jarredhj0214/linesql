select j.*
from dbo.events e
cross apply openjson(e.payload) j;
