SET search_path TO public;
CREATE TEMP VIEW cache AS SELECT event_id FROM ods.events;
CREATE OR REPLACE TEMP VIEW cache AS SELECT event_id FROM ods.archive;
SELECT event_id FROM cache;
WITH cache AS (SELECT event_id FROM ops.pending)
SELECT event_id FROM cache;
SELECT event_id FROM public.cache;
DISCARD TEMP;
SELECT event_id FROM cache;
