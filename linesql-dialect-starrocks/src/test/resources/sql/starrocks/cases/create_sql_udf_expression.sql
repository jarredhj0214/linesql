create function analytics.format_username(name string)
returns concat('USER_', upper(name));
