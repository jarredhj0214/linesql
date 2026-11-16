BEGIN
    EXECUTE IMMEDIATE q'{SELECT 'literal' FROM ghost.rows;
        DBMS_STATS.GATHER_TABLE_STATS('ghost', 'stats');}';
    INSERT INTO mart.audit (id) SELECT id FROM ods.orders;
END;
