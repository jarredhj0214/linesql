BEGIN
  DBMS_STATS.LOCK_TABLE_STATS(ownname => 'MART', tabname => 'USERS');
END;
