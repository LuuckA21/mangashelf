ALTER TABLE users RENAME CONSTRAINT users_pkey TO pk_users;
ALTER TABLE series RENAME CONSTRAINT series_pkey TO pk_series;
ALTER TABLE editions RENAME CONSTRAINT editions_pkey TO pk_editions;
ALTER TABLE collection_editions RENAME CONSTRAINT collection_editions_pkey TO pk_collection_editions;
ALTER TABLE owned_volumes RENAME CONSTRAINT owned_volumes_pkey TO pk_owned_volumes;
