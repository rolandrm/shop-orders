-- Données de démonstration (profil seed, jamais en production). Idempotent : identifiants fixes,
-- ON CONFLICT DO NOTHING. Respecte les contraintes de V1 : line_number à partir de 1,
-- line_total_amount = unit_price_amount * quantity, total_amount = somme des lignes.
-- Les produits cités peuvent être absents de shop-api : la lecture d'une commande ne l'appelle pas.

INSERT INTO orders_order (id, customer_id, created_at, currency, total_amount, item_count, version)
VALUES
    ('0190a000-0000-7000-8000-000000000001', '0190a000-0000-7000-8000-0000000000c1',
     '2026-01-15T10:00:00Z', 'EUR', 45.00, 2, 0),
    ('0190a000-0000-7000-8000-000000000002', '0190a000-0000-7000-8000-0000000000c1',
     '2026-01-16T14:30:00Z', 'EUR', 12.50, 1, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO orders_order_line
    (order_id, line_number, product_id, product_name, unit_price_amount, quantity, line_total_amount)
VALUES
    ('0190a000-0000-7000-8000-000000000001', 1, '0190a000-0000-7000-8000-0000000000d1',
     'Café en grains 1 kg', 15.00, 2, 30.00),
    ('0190a000-0000-7000-8000-000000000001', 2, '0190a000-0000-7000-8000-0000000000d2',
     'Tasse en céramique', 15.00, 1, 15.00),
    ('0190a000-0000-7000-8000-000000000002', 1, '0190a000-0000-7000-8000-0000000000d3',
     'Filtre papier x100', 2.50, 5, 12.50)
ON CONFLICT (order_id, line_number) DO NOTHING;
