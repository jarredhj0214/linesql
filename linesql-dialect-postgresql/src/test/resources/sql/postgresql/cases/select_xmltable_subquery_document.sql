SELECT xmltable.*
FROM XMLTABLE('/document/entry'
    PASSING (SELECT d.payload FROM raw.docs d WHERE d.id = 7)
    COLUMNS code text PATH '@code'
);
