select f.value
from table(app.split_tags('a,b')) f;
