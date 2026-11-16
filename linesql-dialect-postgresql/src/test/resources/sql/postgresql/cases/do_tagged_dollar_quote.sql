do $Body$
begin
    perform '$body$;';
    perform '$$';
end;
$Body$ language plpgsql;
