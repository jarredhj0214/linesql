create user if not exists 'report'@'%'
identified with caching_sha2_password by 'secret'
require ssl
password expire interval 90 day
account lock
comment 'reporting account'
attribute '{"owner":"data-platform"}'
failed_login_attempts 5
password_lock_time 2;
