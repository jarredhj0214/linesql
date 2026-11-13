COPY mart.users FROM STDIN WITH (FORMAT csv, FORCE_NULL (email), FORCE_NOT_NULL (name));
