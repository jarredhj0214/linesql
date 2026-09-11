alter system restore
from 'file:///ob_backup/data,file:///ob_backup/archive'
until scn = 1712650554000909004
preview;
