PREPARE "Lookup"(integer) AS SELECT id FROM ods.events WHERE id > $1;
PREPARE lookup(integer) AS UPDATE mart.jobs
SET attempts = attempts + 1 WHERE id = $1 RETURNING id;
EXECUTE "Lookup"(10);
DISCARD PLANS;
EXECUTE lookup(1);
DEALLOCATE PREPARE "Lookup";
EXECUTE "Lookup"(10);
EXECUTE LOOKUP(2);
DEALLOCATE ALL;
EXECUTE lookup(3);
PREPARE next_lookup AS SELECT name FROM ods.accounts;
DISCARD ALL;
EXECUTE next_lookup;
