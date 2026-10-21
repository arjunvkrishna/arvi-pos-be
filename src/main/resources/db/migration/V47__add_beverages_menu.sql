-- ============================================================
-- NERAM POS - V47: Add Beverages Menu
-- Adds 8 beverage categories (Lassi, Juices, Mojitos & Coolers,
-- Shakes, Coffee & Tea (Cold), Smoothies, Falooda & Milk, Hot Coffee & Tea)
-- and 52 new beverage items.
-- ============================================================

-- Step 1: Insert 8 Beverage Categories
INSERT INTO categories (name, description, icon, sort_order) VALUES
  ('Lassi',                  'Traditional chilled yogurt lassi varieties',                 'fa-glass-water',         22),
  ('Juices',                 'Freshly squeezed fruit juices and revitalizing blends',      'fa-lemon',               23),
  ('Mojitos & Coolers',      'Refreshing iced mojitos, mocktails and summer coolers',      'fa-martini-glass-citrus', 24),
  ('Shakes',                 'Rich and creamy thickshakes and specialty fruit shakes',     'fa-blender',             25),
  ('Coffee & Tea (Cold)',    'Chilled gourmet iced teas and iced coffee specialties',      'fa-glass-whiskey',       26),
  ('Smoothies',              'Nutritious fresh fruit smoothies and blended drinks',        'fa-apple-whole',         27),
  ('Falooda & Milk',         'Decadent layered royal faloodas and traditional aval milk',  'fa-ice-cream',           28),
  ('Hot Coffee & Tea',       'Steaming artisanal coffees, filter coffee and spiced teas',  'fa-mug-hot',             29)
ON CONFLICT (name) DO NOTHING;

-- Step 2: Insert 52 Beverage Items

-- 1. LASSI (5 Items)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Sweet Lassi',                     (SELECT id FROM categories WHERE name = 'Lassi'), 120.0, TRUE, TRUE),
  ('Mango Lassi',                     (SELECT id FROM categories WHERE name = 'Lassi'), 130.0, TRUE, TRUE),
  ('Rose Lassi',                      (SELECT id FROM categories WHERE name = 'Lassi'), 130.0, TRUE, TRUE),
  ('Dry Fruit Lassi',                 (SELECT id FROM categories WHERE name = 'Lassi'), 150.0, TRUE, TRUE),
  ('Salted / Masala Lassi',           (SELECT id FROM categories WHERE name = 'Lassi'), 160.0, TRUE, TRUE);

-- 2. JUICES (8 Items)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Fresh Orange Juice',              (SELECT id FROM categories WHERE name = 'Juices'), 110.0, TRUE, TRUE),
  ('Watermelon Juice',                (SELECT id FROM categories WHERE name = 'Juices'),  80.0, TRUE, TRUE),
  ('Pineapple Juice',                 (SELECT id FROM categories WHERE name = 'Juices'),  90.0, TRUE, TRUE),
  ('Pomegranate Juice',               (SELECT id FROM categories WHERE name = 'Juices'), 130.0, TRUE, TRUE),
  ('Mixed Fruit Juice',               (SELECT id FROM categories WHERE name = 'Juices'), 130.0, TRUE, TRUE),
  ('ABC (Apple Beetroot Carrot) Juice', (SELECT id FROM categories WHERE name = 'Juices'), 130.0, TRUE, TRUE),
  ('Grape Juice',                     (SELECT id FROM categories WHERE name = 'Juices'),  90.0, TRUE, TRUE),
  ('Mango Juice',                     (SELECT id FROM categories WHERE name = 'Juices'), 120.0, TRUE, TRUE);

-- 3. MOJITOS & COOLERS (9 Items)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Original Mint Mojito',            (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 140.0, TRUE, TRUE),
  ('Passion Fruit Mojito',            (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 160.0, TRUE, TRUE),
  ('Watermelon Mojito',               (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 160.0, TRUE, TRUE),
  ('Green Apple Mojito',              (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 120.0, TRUE, TRUE),
  ('Fruit Punch',                     (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 140.0, TRUE, TRUE),
  ('Blue Lagoon',                     (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 130.0, TRUE, TRUE),
  ('Dark Lime',                       (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 130.0, TRUE, TRUE),
  ('Life of 3',                       (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 150.0, TRUE, TRUE),
  ('Kannur Cocktail',                 (SELECT id FROM categories WHERE name = 'Mojitos & Coolers'), 130.0, TRUE, TRUE);

-- 4. SHAKES (12 Items)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Chocolate Fudge Shake',           (SELECT id FROM categories WHERE name = 'Shakes'), 170.0, TRUE, TRUE),
  ('Oreo Thickshake',                 (SELECT id FROM categories WHERE name = 'Shakes'), 160.0, TRUE, TRUE),
  ('KitKat Thickshake',               (SELECT id FROM categories WHERE name = 'Shakes'), 170.0, TRUE, TRUE),
  ('Strawberry Shake',                (SELECT id FROM categories WHERE name = 'Shakes'), 140.0, TRUE, TRUE),
  ('Vanilla Bean Shake',              (SELECT id FROM categories WHERE name = 'Shakes'), 130.0, TRUE, TRUE),
  ('Mango Shake',                     (SELECT id FROM categories WHERE name = 'Shakes'), 140.0, TRUE, TRUE),
  ('Protein Shake',                   (SELECT id FROM categories WHERE name = 'Shakes'), 190.0, TRUE, TRUE),
  ('Avocado Shake',                   (SELECT id FROM categories WHERE name = 'Shakes'), 180.0, TRUE, TRUE),
  ('Butter Shake',                    (SELECT id FROM categories WHERE name = 'Shakes'), 140.0, TRUE, TRUE),
  ('Chikku Shake',                    (SELECT id FROM categories WHERE name = 'Shakes'), 130.0, TRUE, TRUE),
  ('Omani Shake',                     (SELECT id FROM categories WHERE name = 'Shakes'), 170.0, TRUE, TRUE),
  ('Arabian Shake',                   (SELECT id FROM categories WHERE name = 'Shakes'), 160.0, TRUE, TRUE);

-- 5. COFFEE & TEA (COLD) (4 Items)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Iced Lemon and Mint Tea',         (SELECT id FROM categories WHERE name = 'Coffee & Tea (Cold)'), 110.0, TRUE, TRUE),
  ('Orange Tea',                      (SELECT id FROM categories WHERE name = 'Coffee & Tea (Cold)'), 120.0, TRUE, TRUE),
  ('Ginger Watermelon Tea',           (SELECT id FROM categories WHERE name = 'Coffee & Tea (Cold)'), 180.0, TRUE, TRUE),
  ('Cold Coffee with Ice Cream',      (SELECT id FROM categories WHERE name = 'Coffee & Tea (Cold)'), 200.0, TRUE, TRUE);

-- 6. SMOOTHIES (5 Items)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Chocolate Smoothie',              (SELECT id FROM categories WHERE name = 'Smoothies'), 200.0, TRUE, TRUE),
  ('Banana Smoothie',                 (SELECT id FROM categories WHERE name = 'Smoothies'), 210.0, TRUE, TRUE),
  ('Papaya Smoothie',                 (SELECT id FROM categories WHERE name = 'Smoothies'), 200.0, TRUE, TRUE),
  ('Mango Smoothie',                  (SELECT id FROM categories WHERE name = 'Smoothies'), 220.0, TRUE, TRUE),
  ('Coconut Smoothie',                (SELECT id FROM categories WHERE name = 'Smoothies'), 230.0, TRUE, TRUE);

-- 7. FALOODA & MILK (3 Items)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Royal Falooda (Pista / Strawberry / Butterscotch)', (SELECT id FROM categories WHERE name = 'Falooda & Milk'), 190.0, TRUE, TRUE),
  ('Falooda (Strawberry / Butterscotch / Pista)',       (SELECT id FROM categories WHERE name = 'Falooda & Milk'), 150.0, TRUE, TRUE),
  ('Aval Milk',                                         (SELECT id FROM categories WHERE name = 'Falooda & Milk'),  90.0, TRUE, TRUE);

-- 8. HOT COFFEE & TEA (6 Items)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Normal Coffee',                   (SELECT id FROM categories WHERE name = 'Hot Coffee & Tea'), 30.0, TRUE, TRUE),
  ('Brew Coffee',                     (SELECT id FROM categories WHERE name = 'Hot Coffee & Tea'), 50.0, TRUE, TRUE),
  ('Classic Coffee',                  (SELECT id FROM categories WHERE name = 'Hot Coffee & Tea'), 40.0, TRUE, TRUE),
  ('Filter Coffee',                   (SELECT id FROM categories WHERE name = 'Hot Coffee & Tea'), 40.0, TRUE, TRUE),
  ('Masala Tea',                      (SELECT id FROM categories WHERE name = 'Hot Coffee & Tea'), 30.0, TRUE, TRUE),
  ('Malai Tea',                       (SELECT id FROM categories WHERE name = 'Hot Coffee & Tea'), 40.0, TRUE, TRUE);
