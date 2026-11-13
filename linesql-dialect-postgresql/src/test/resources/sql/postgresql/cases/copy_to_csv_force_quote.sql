COPY mart.users TO STDOUT WITH (FORMAT csv, FORCE_QUOTE (name, email));
