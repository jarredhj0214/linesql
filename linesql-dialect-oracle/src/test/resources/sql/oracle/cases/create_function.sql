create or replace function mart.normalize_name(p_name varchar2) return varchar2 as begin return lower(p_name) end;
