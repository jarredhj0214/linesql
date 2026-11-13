select j.[key], j.value, j.type
from openjson(N'{"priority":1}') j;
