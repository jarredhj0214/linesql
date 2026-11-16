UPDATE TOP (@count) dbo.users
SET retries = retries + 1
WHERE active = 1;
