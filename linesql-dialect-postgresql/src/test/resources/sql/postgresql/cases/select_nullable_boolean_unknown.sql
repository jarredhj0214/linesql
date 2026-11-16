SELECT e.approved IS NOT UNKNOWN AS approval_known,
       e.unknown
FROM ods.events e
WHERE e.approved IS UNKNOWN;
