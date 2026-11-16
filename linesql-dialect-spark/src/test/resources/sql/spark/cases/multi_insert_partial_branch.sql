FROM ods.users
INSERT INTO TABLE mart.user_ids SELECT id
INSERT INTO TABLE mart.user_names SELECT concat(given_name, family_name);
