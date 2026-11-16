CREATE PROCEDURE mart.refresh(IN p_id bigint, INOUT p_count bigint, OUT p_status text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path FROM CURRENT
AS $proc$ BEGIN p_status := 'done'; END; $proc$;
