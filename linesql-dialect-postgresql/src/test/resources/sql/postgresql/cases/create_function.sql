create or replace function mart.normalize_name(name text)
returns text
language sql
as 'select lower(name)';
