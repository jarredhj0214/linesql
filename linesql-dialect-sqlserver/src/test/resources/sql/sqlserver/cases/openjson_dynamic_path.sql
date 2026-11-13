select j.value
from dbo.events e
cross apply openjson(e.payload, e.json_path) j
where j.type = 1;
