select e.id
from ods.events tablesample(10 percent) e
