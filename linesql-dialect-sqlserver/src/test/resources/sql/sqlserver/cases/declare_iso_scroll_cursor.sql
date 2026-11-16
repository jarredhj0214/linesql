DECLARE report_cursor INSENSITIVE SCROLL CURSOR
FOR SELECT amount FROM warehouse.orders WHERE active = 1
FOR READ_ONLY;
