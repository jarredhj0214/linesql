create or replace global function format_date_display(dt datetime)
returns concat(year(dt), '-', lpad(month(dt), 2, '0'), '-', lpad(day(dt), 2, '0'));
