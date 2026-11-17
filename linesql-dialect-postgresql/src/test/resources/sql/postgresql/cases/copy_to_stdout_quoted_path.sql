COPY "sales.daily"."current.events" ("event.id", payload)
TO STDOUT WITH (FORMAT CSV, HEADER TRUE);
