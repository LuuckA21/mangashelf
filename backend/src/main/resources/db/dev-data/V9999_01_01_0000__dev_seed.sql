-- Dev profile only. Users: admin / admin-dev-password, amico / amico-dev-password.
INSERT INTO users (id, username, password_hash, role, must_change_password, created_at, updated_at) VALUES
('0190a000-0000-7000-8000-000000000001', 'admin',
 '{bcrypt}$2a$10$mc3HF4jEXox9hZW4pgtjpunIS2cwbCoJvTSKK9ojGFGKypxFoJbtq', 'ADMIN', false, now(), now()),
('0190a000-0000-7000-8000-000000000002', 'amico',
 '{bcrypt}$2a$10$93hSYYQNvD3oI4mkIhhIHO0jcTQrSoHl6ckRLBIgzplJA0hTYfNvm', 'USER', false, now(), now());

INSERT INTO series (id, title, created_by, created_at, updated_at) VALUES
('0190a000-0000-7000-8000-000000000010', 'One Piece', '0190a000-0000-7000-8000-000000000001', now(), now());

INSERT INTO editions (id, series_id, name, type, status, volume_count, created_by, version, created_at, updated_at) VALUES
('0190a000-0000-7000-8000-000000000021', '0190a000-0000-7000-8000-000000000010', 'Edizione blu', 'REGULAR', 'ONGOING', 114, '0190a000-0000-7000-8000-000000000001', 0, now(), now()),
('0190a000-0000-7000-8000-000000000022', '0190a000-0000-7000-8000-000000000010', 'Volume 1 - Oro', 'VARIANT', 'COMPLETED', 1, '0190a000-0000-7000-8000-000000000001', 0, now(), now()),
('0190a000-0000-7000-8000-000000000023', '0190a000-0000-7000-8000-000000000010', 'Volume 100 - Giapponese', 'JAPANESE', 'COMPLETED', 1, '0190a000-0000-7000-8000-000000000001', 0, now(), now()),
('0190a000-0000-7000-8000-000000000024', '0190a000-0000-7000-8000-000000000010', 'Novel Ace', 'REGULAR', 'COMPLETED', 2, '0190a000-0000-7000-8000-000000000001', 0, now(), now());

INSERT INTO collection_editions (id, user_id, edition_id, created_at, updated_at) VALUES
('0190a000-0000-7000-8000-000000000031', '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000021', now(), now()),
('0190a000-0000-7000-8000-000000000032', '0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-000000000022', now(), now());

INSERT INTO owned_volumes (id, collection_edition_id, number, created_at, updated_at)
SELECT gen_random_uuid(), '0190a000-0000-7000-8000-000000000031', n, now(), now()
FROM generate_series(1, 110) AS n;
