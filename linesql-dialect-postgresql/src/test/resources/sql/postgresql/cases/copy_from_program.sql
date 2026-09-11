copy mart.server_logs (host, line)
from program 'gzip -dc /var/log/app/*.gz'
with (format csv, delimiter '|');
