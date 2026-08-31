select v.id, v.name
from (values row(1, 'Alice'), row(2, 'Bob')) as v(id, name);
