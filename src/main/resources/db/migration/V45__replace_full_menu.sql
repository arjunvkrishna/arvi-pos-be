-- ============================================================
-- NERAM POS - V45: Replace Full Menu (Categories & Items)
-- Removes all existing categories and menu items and replaces
-- with the correct v2.4.2 menu as approved by Neram management.
-- ============================================================

-- Step 1: Clear existing menu items and categories
-- Detach bill_items from old menu_items (item_name/price/subtotal preserved on bill_items)
ALTER TABLE bill_items DROP CONSTRAINT IF EXISTS fk_bill_items_menu;
ALTER TABLE bill_items ADD CONSTRAINT fk_bill_items_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE SET NULL;
UPDATE bill_items SET menu_item_id = NULL WHERE menu_item_id IS NOT NULL;

DELETE FROM recipes;
DELETE FROM menu_items;
DELETE FROM categories;

-- Reset sequences
ALTER SEQUENCE menu_items_id_seq RESTART WITH 1;
ALTER SEQUENCE categories_id_seq RESTART WITH 1;

-- ============================================================
-- Step 2: Insert correct categories (21 categories)
-- ============================================================
INSERT INTO categories (name, description, icon, sort_order) VALUES
  ('Starters',                    'Crispy appetizers, finger foods and quick bites',              'fa-cookie-bite',              1),
  ('Egg Preparations',            'Farm fresh egg omelettes served with garlic bread',            'fa-egg',                      2),
  ('Soups',                       'Warm and comforting soups served with accompaniments',         'fa-bowl-food',                3),
  ('Sandwiches',                  'Toasted and grilled gourmet sandwiches with french fries',    'fa-bread-slice',              4),
  ('Wrappers',                    'Flavour-packed rolled wraps and loaded quesadillas',           'fa-scroll',                   5),
  ('Salads',                      'Fresh crisp greens, artisanal dressings and vibrant toppings','fa-leaf',                     6),
  ('Fried Chicken',               'Crispy golden fried chicken served with seasoned fries',      'fa-drumstick-bite',           7),
  ('Burgers',                     'Juicy grilled patties in soft buns served with fries',        'fa-burger',                   8),
  ('Signature Food',              'Signature gourmet specialties crafted to perfection',         'fa-crown',                    9),
  ('Pastas',                      'Authentic Italian pasta in rich savory sauces',               'fa-bowl-rice',                10),
  ('Desserts',                    'Artisanal sweet desserts, souffles and flan',                 'fa-ice-cream',                11),
  ('Family Combo',                'Value family meal combinations',                              'fa-users',                    12),
  ('Grilled Food',                'Sizzling grilled steaks, chicken and marinated seafood',      'fa-fire',                     13),
  ('Fried Chicken Varieties',     'Classic and specialty fried chicken preparations',            'fa-drumstick-bite',           14),
  ('Chinese Food',                'Authentic Chinese noodles, rice and Indo-Chinese specialties','fa-bowl-rice',                15),
  ('Special Food',                'Chef special platters and value combinations',                'fa-star',                     16),
  ('Shrimp & Prawns Varieties',   'Golden fried and sauced prawn and shrimp specialties',       'fa-shrimp',                   17),
  ('High-Protein & Gym Bowls',    'Nutritious protein-packed bowls and power salads',            'fa-dumbbell',                 18),
  ('Side Dishes',                 'Crispy sides and seasoned accompaniments',                    'fa-utensils',                 19),
  ('Loaded Fries',                'Indulgent loaded fries with premium toppings',               'fa-plate-wheat',              20),
  ('Rice Meals',                  'Hearty rice meals served with accompaniments',               'fa-bowl-rice',                21);

-- ============================================================
-- Step 3: Insert correct menu items
-- ============================================================

-- Helper: reference category by name
-- STARTERS (category 1)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Fries with Spicy Mayo',        (SELECT id FROM categories WHERE name = 'Starters'), 180.0, TRUE,  TRUE),
  ('Cheesy Chicken Fries',         (SELECT id FROM categories WHERE name = 'Starters'), 250.0, FALSE, TRUE),
  ('Mexican Chicken Nachos',       (SELECT id FROM categories WHERE name = 'Starters'), 310.0, FALSE, TRUE),
  ('Loaded Fries (Chicken)',        (SELECT id FROM categories WHERE name = 'Starters'), 320.0, FALSE, TRUE),
  ('Loaded Fries (Beef)',           (SELECT id FROM categories WHERE name = 'Starters'), 340.0, FALSE, TRUE),
  ('Veg Spring Roll',              (SELECT id FROM categories WHERE name = 'Starters'), 210.0, TRUE,  TRUE),
  ('Veg Nuggets',                  (SELECT id FROM categories WHERE name = 'Starters'), 200.0, TRUE,  TRUE),
  ('Cheese Garlic Bread',          (SELECT id FROM categories WHERE name = 'Starters'), 220.0, TRUE,  TRUE),
  ('Fried Chicken Wings',          (SELECT id FROM categories WHERE name = 'Starters'), 290.0, FALSE, TRUE),
  ('Chicken Nuggets',              (SELECT id FROM categories WHERE name = 'Starters'), 240.0, FALSE, TRUE),
  ('Fish Nuggets',                 (SELECT id FROM categories WHERE name = 'Starters'), 280.0, FALSE, TRUE),
  ('Chicken Popcorn',              (SELECT id FROM categories WHERE name = 'Starters'), 240.0, FALSE, TRUE),
  ('Chicken Lollipop',             (SELECT id FROM categories WHERE name = 'Starters'), 310.0, FALSE, TRUE),
  ('Fish Finger',                  (SELECT id FROM categories WHERE name = 'Starters'), 340.0, FALSE, TRUE),
  ('Peri-Peri Potato',             (SELECT id FROM categories WHERE name = 'Starters'), 190.0, TRUE,  TRUE),
  ('Veggies',                      (SELECT id FROM categories WHERE name = 'Starters'), 190.0, TRUE,  TRUE),
  ('Golden Cheese Balls',          (SELECT id FROM categories WHERE name = 'Starters'), 280.0, TRUE,  TRUE),
  ('Cheese Chilli Toast',          (SELECT id FROM categories WHERE name = 'Starters'), 210.0, TRUE,  TRUE),
  ('Chicken Spring Roll',          (SELECT id FROM categories WHERE name = 'Starters'), 250.0, FALSE, TRUE);

-- EGG PREPARATIONS (category 2)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Cheese and Tomato Omelette',   (SELECT id FROM categories WHERE name = 'Egg Preparations'), 190.0, FALSE, TRUE),
  ('Plain Omelette',               (SELECT id FROM categories WHERE name = 'Egg Preparations'), 120.0, FALSE, TRUE),
  ('Cheese Omelette',              (SELECT id FROM categories WHERE name = 'Egg Preparations'), 150.0, FALSE, TRUE),
  ('Scrambled Eggs',               (SELECT id FROM categories WHERE name = 'Egg Preparations'), 140.0, FALSE, TRUE),
  ('Baked Omelette',               (SELECT id FROM categories WHERE name = 'Egg Preparations'), 200.0, FALSE, TRUE);

-- SOUPS (category 3)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Minestrone Soup (Chicken)',    (SELECT id FROM categories WHERE name = 'Soups'), 230.0, FALSE, TRUE),
  ('Minestrone Soup (Veg)',        (SELECT id FROM categories WHERE name = 'Soups'), 190.0, TRUE,  TRUE),
  ('Cream of Chicken Soup',        (SELECT id FROM categories WHERE name = 'Soups'), 230.0, FALSE, TRUE),
  ('Cream of Vegetable Soup',      (SELECT id FROM categories WHERE name = 'Soups'), 190.0, TRUE,  TRUE),
  ('Hot and Sour Soup (Veg)',      (SELECT id FROM categories WHERE name = 'Soups'), 150.0, TRUE,  TRUE),
  ('Hot and Sour Soup (Chicken)',  (SELECT id FROM categories WHERE name = 'Soups'), 220.0, FALSE, TRUE),
  ('Sweet Corn Soup (Veg)',        (SELECT id FROM categories WHERE name = 'Soups'), 180.0, TRUE,  TRUE),
  ('Sweet Corn Soup (Chicken)',    (SELECT id FROM categories WHERE name = 'Soups'), 220.0, FALSE, TRUE),
  ('Manchow Soup (Veg)',           (SELECT id FROM categories WHERE name = 'Soups'), 190.0, TRUE,  TRUE),
  ('Manchow Soup (Chicken)',       (SELECT id FROM categories WHERE name = 'Soups'), 230.0, FALSE, TRUE),
  ('Clear Soup (Veg)',             (SELECT id FROM categories WHERE name = 'Soups'), 160.0, TRUE,  TRUE),
  ('Clear Soup (Chicken)',         (SELECT id FROM categories WHERE name = 'Soups'), 200.0, FALSE, TRUE),
  ('Lemon Coriander Soup',         (SELECT id FROM categories WHERE name = 'Soups'), 180.0, TRUE,  TRUE);

-- SANDWICHES (category 4)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Grilled Cheese and Tomato Sandwich',              (SELECT id FROM categories WHERE name = 'Sandwiches'), 210.0, TRUE,  TRUE),
  ('Chicken Club Sandwich',                           (SELECT id FROM categories WHERE name = 'Sandwiches'), 330.0, FALSE, TRUE),
  ('Chicken Hot Garlic Sandwich',                     (SELECT id FROM categories WHERE name = 'Sandwiches'), 280.0, FALSE, TRUE),
  ('Grilled Chicken, Cheese and Jalapeño Sandwich',   (SELECT id FROM categories WHERE name = 'Sandwiches'), 290.0, FALSE, TRUE),
  ('Veg Club Sandwich',                               (SELECT id FROM categories WHERE name = 'Sandwiches'), 250.0, TRUE,  TRUE),
  ('Veg Grilled Cheese Sandwich',                     (SELECT id FROM categories WHERE name = 'Sandwiches'), 210.0, TRUE,  TRUE),
  ('Egg Mayo Sandwich',                               (SELECT id FROM categories WHERE name = 'Sandwiches'), 190.0, FALSE, TRUE),
  ('Boiled Egg and Pepper Sandwich',                  (SELECT id FROM categories WHERE name = 'Sandwiches'), 180.0, FALSE, TRUE);

-- WRAPPERS (category 5)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Mexican Chicken Wrap',         (SELECT id FROM categories WHERE name = 'Wrappers'), 280.0, FALSE, TRUE),
  ('Crispy Chicken Fajita Wrap',   (SELECT id FROM categories WHERE name = 'Wrappers'), 290.0, FALSE, TRUE),
  ('BBQ Chicken Fajita Wrap',      (SELECT id FROM categories WHERE name = 'Wrappers'), 290.0, FALSE, TRUE),
  ('Veg Fajita Wrap',              (SELECT id FROM categories WHERE name = 'Wrappers'), 220.0, TRUE,  TRUE),
  ('Mexican Chicken Quesadilla',   (SELECT id FROM categories WHERE name = 'Wrappers'), 320.0, FALSE, TRUE);

-- SALADS (category 6)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Greek Salad',                           (SELECT id FROM categories WHERE name = 'Salads'), 230.0, TRUE,  TRUE),
  ('Watermelon Feta Salad',                 (SELECT id FROM categories WHERE name = 'Salads'), 260.0, TRUE,  TRUE),
  ('Grilled Chicken with Balsamic Dressing',(SELECT id FROM categories WHERE name = 'Salads'), 330.0, FALSE, TRUE),
  ('Garden Fresh Green Salad',              (SELECT id FROM categories WHERE name = 'Salads'), 170.0, TRUE,  TRUE),
  ('Sprouted Moong Salad',                  (SELECT id FROM categories WHERE name = 'Salads'), 180.0, TRUE,  TRUE),
  ('Pasta Salad (Veg)',                     (SELECT id FROM categories WHERE name = 'Salads'), 240.0, TRUE,  TRUE),
  ('Pasta Salad (Chicken)',                 (SELECT id FROM categories WHERE name = 'Salads'), 290.0, FALSE, TRUE),
  ('Chicken Caesar Salad',                  (SELECT id FROM categories WHERE name = 'Salads'), 330.0, FALSE, TRUE),
  ('Chicken Cobb Salad',                    (SELECT id FROM categories WHERE name = 'Salads'), 340.0, FALSE, TRUE),
  ('Boiled Vegetables',                     (SELECT id FROM categories WHERE name = 'Salads'), 280.0, TRUE,  TRUE);

-- FRIED CHICKEN (category 7)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('2-Piece Chicken Legs with Fries',              (SELECT id FROM categories WHERE name = 'Fried Chicken'), 280.0, FALSE, TRUE),
  ('4-Piece Chicken Legs & Thighs with Fries',     (SELECT id FROM categories WHERE name = 'Fried Chicken'), 490.0, FALSE, TRUE),
  ('6-Piece Chicken Strips with Fries',            (SELECT id FROM categories WHERE name = 'Fried Chicken'), 380.0, FALSE, TRUE);

-- BURGERS (category 8)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Veg Burger',                                              (SELECT id FROM categories WHERE name = 'Burgers'), 220.0, TRUE,  TRUE),
  ('Crispy Fried Chicken Burger',                             (SELECT id FROM categories WHERE name = 'Burgers'), 310.0, FALSE, TRUE),
  ('Chicken Burger',                                          (SELECT id FROM categories WHERE name = 'Burgers'), 280.0, FALSE, TRUE),
  ('Classic Beef Burger',                                     (SELECT id FROM categories WHERE name = 'Burgers'), 350.0, FALSE, TRUE),
  ('Neram Special Jumbo Beef Burger',                         (SELECT id FROM categories WHERE name = 'Burgers'), 460.0, FALSE, TRUE),
  ('Classic Double Cheeseburger (Smashed)',                   (SELECT id FROM categories WHERE name = 'Burgers'), 410.0, FALSE, TRUE),
  ('Neram Special Cheese Volcano Burger (Molten Cheese Lava Bun)', (SELECT id FROM categories WHERE name = 'Burgers'), 440.0, FALSE, TRUE),
  ('Crispy Spicy Zinger Burger',                              (SELECT id FROM categories WHERE name = 'Burgers'), 320.0, FALSE, TRUE),
  ('Ultimate Veggie Patty Burger',                            (SELECT id FROM categories WHERE name = 'Burgers'), 230.0, TRUE,  TRUE),
  ('Mexican Jalapeño & Salsa Burger',                         (SELECT id FROM categories WHERE name = 'Burgers'), 330.0, FALSE, TRUE),
  ('Chef''s Special Neram Signature Burger',                  (SELECT id FROM categories WHERE name = 'Burgers'), 480.0, FALSE, TRUE);

-- SIGNATURE FOOD (category 9)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Neram Special Chicken Paella with Spanish',  (SELECT id FROM categories WHERE name = 'Signature Food'), 480.0, FALSE, TRUE),
  ('Neram Special Chicken Cordon Bleu',          (SELECT id FROM categories WHERE name = 'Signature Food'), 440.0, FALSE, TRUE),
  ('Chicken Donut',                              (SELECT id FROM categories WHERE name = 'Signature Food'), 270.0, FALSE, TRUE);

-- PASTAS (category 10)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Chicken Spaghetti Bolognese',                                              (SELECT id FROM categories WHERE name = 'Pastas'), 410.0, FALSE, TRUE),
  ('Chicken Macaroni',                                                         (SELECT id FROM categories WHERE name = 'Pastas'), 320.0, FALSE, TRUE),
  ('Chicken Alfredo',                                                          (SELECT id FROM categories WHERE name = 'Pastas'), 370.0, FALSE, TRUE),
  ('Neram Special Lasagne',                                                    (SELECT id FROM categories WHERE name = 'Pastas'), 450.0, FALSE, TRUE),
  ('Pasta Aglio e Olio Peperoncino (Veg)',                                     (SELECT id FROM categories WHERE name = 'Pastas'), 250.0, TRUE,  TRUE),
  ('Pasta Aglio e Olio Peperoncino (Chicken/Special)',                         (SELECT id FROM categories WHERE name = 'Pastas'), 340.0, FALSE, TRUE),
  ('Penne Arrabbiata (Red Sauce) or Fettuccine Alfredo (White Sauce)',         (SELECT id FROM categories WHERE name = 'Pastas'), 310.0, TRUE,  TRUE),
  ('Creamy Pesto Pasta',                                                       (SELECT id FROM categories WHERE name = 'Pastas'), 320.0, TRUE,  TRUE),
  ('Mac and Cheese',                                                           (SELECT id FROM categories WHERE name = 'Pastas'), 330.0, TRUE,  TRUE),
  ('Mexican Pasta/Cajun Pasta',                                                (SELECT id FROM categories WHERE name = 'Pastas'), 340.0, FALSE, TRUE),
  ('Penne Pasta/Farfalle Pasta',                                               (SELECT id FROM categories WHERE name = 'Pastas'), 320.0, TRUE,  TRUE),
  ('Beef Pasta (Red and White Sauce)',                                         (SELECT id FROM categories WHERE name = 'Pastas'), 390.0, FALSE, TRUE),
  ('Pesto Pasta',                                                              (SELECT id FROM categories WHERE name = 'Pastas'), 340.0, TRUE,  TRUE);

-- DESSERTS (category 11)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Dates and Nuts Flan',                (SELECT id FROM categories WHERE name = 'Desserts'), 210.0, TRUE, TRUE),
  ('Neram Special Orange Crème Brûlée',  (SELECT id FROM categories WHERE name = 'Desserts'), 260.0, TRUE, TRUE),
  ('Mango Soufflé',                      (SELECT id FROM categories WHERE name = 'Desserts'), 220.0, TRUE, TRUE),
  ('Brownie',                            (SELECT id FROM categories WHERE name = 'Desserts'), 160.0, TRUE, TRUE),
  ('Brownie with Ice Cream',             (SELECT id FROM categories WHERE name = 'Desserts'), 210.0, TRUE, TRUE),
  ('Chocolate Soufflé',                  (SELECT id FROM categories WHERE name = 'Desserts'), 230.0, TRUE, TRUE);

-- FAMILY COMBO (category 12)
INSERT INTO menu_items (name, category_id, price, description, is_veg, is_available) VALUES
  ('Family Club Combo (2 Burgers + 2 Sandwiches + 2 Natafi Wraps + French Fries + 1 L Pepsi)',
   (SELECT id FROM categories WHERE name = 'Family Combo'),
   1099.0,
   '2 Burgers + 2 Sandwiches + 2 Natafi Wraps + French Fries + 1 L Pepsi',
   FALSE, TRUE);

-- GRILLED FOOD (category 13)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Neram Special Fish and Chips',       (SELECT id FROM categories WHERE name = 'Grilled Food'), 450.0, FALSE, TRUE),
  ('Green Masala Chicken Leg',           (SELECT id FROM categories WHERE name = 'Grilled Food'), 340.0, FALSE, TRUE),
  ('Neram Special Chicken Parmigiana',   (SELECT id FROM categories WHERE name = 'Grilled Food'), 440.0, FALSE, TRUE),
  ('Neram Special Sirloin Steak',        (SELECT id FROM categories WHERE name = 'Grilled Food'), 580.0, FALSE, TRUE),
  ('Neram Special Ribeye Steak',         (SELECT id FROM categories WHERE name = 'Grilled Food'), 660.0, FALSE, TRUE),
  ('Chicken Monoko',                     (SELECT id FROM categories WHERE name = 'Grilled Food'), 460.0, FALSE, TRUE),
  ('Chicken Steak',                      (SELECT id FROM categories WHERE name = 'Grilled Food'), 410.0, FALSE, TRUE),
  ('Beef Monoko',                        (SELECT id FROM categories WHERE name = 'Grilled Food'), 460.0, FALSE, TRUE),
  ('Beef Steak',                         (SELECT id FROM categories WHERE name = 'Grilled Food'), 490.0, FALSE, TRUE),
  ('Beef Stroganoff',                    (SELECT id FROM categories WHERE name = 'Grilled Food'), 520.0, FALSE, TRUE);

-- FRIED CHICKEN VARIETIES (category 14)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Classic Crispy Fried Chicken',   (SELECT id FROM categories WHERE name = 'Fried Chicken Varieties'), 370.0, FALSE, TRUE),
  ('Spicy Hot Wings',                (SELECT id FROM categories WHERE name = 'Fried Chicken Varieties'), 310.0, FALSE, TRUE),
  ('Popcorn Chicken',                (SELECT id FROM categories WHERE name = 'Fried Chicken Varieties'), 240.0, FALSE, TRUE),
  ('Korean Glazed Fried Chicken',    (SELECT id FROM categories WHERE name = 'Fried Chicken Varieties'), 390.0, FALSE, TRUE),
  ('Chicken Tenders',                (SELECT id FROM categories WHERE name = 'Fried Chicken Varieties'), 280.0, FALSE, TRUE),
  ('Chicken Strips',                 (SELECT id FROM categories WHERE name = 'Fried Chicken Varieties'), 280.0, FALSE, TRUE);

-- CHINESE FOOD (category 15)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Hakka Noodles',          (SELECT id FROM categories WHERE name = 'Chinese Food'), 250.0, TRUE,  TRUE),
  ('Schezwan Fried Rice',    (SELECT id FROM categories WHERE name = 'Chinese Food'), 290.0, TRUE,  TRUE),
  ('Veg Manchurian',         (SELECT id FROM categories WHERE name = 'Chinese Food'), 230.0, TRUE,  TRUE),
  ('Chicken Manchurian',     (SELECT id FROM categories WHERE name = 'Chinese Food'), 310.0, FALSE, TRUE),
  ('Chilli Chicken',         (SELECT id FROM categories WHERE name = 'Chinese Food'), 310.0, FALSE, TRUE),
  ('Chilli Paneer',          (SELECT id FROM categories WHERE name = 'Chinese Food'), 290.0, TRUE,  TRUE),
  ('Spring Rolls',           (SELECT id FROM categories WHERE name = 'Chinese Food'), 210.0, TRUE,  TRUE),
  ('Chilli Gobi',            (SELECT id FROM categories WHERE name = 'Chinese Food'), 220.0, TRUE,  TRUE),
  ('Chicken Fried Rice',     (SELECT id FROM categories WHERE name = 'Chinese Food'), 280.0, FALSE, TRUE),
  ('Veg Fried Rice',         (SELECT id FROM categories WHERE name = 'Chinese Food'), 220.0, TRUE,  TRUE),
  ('Noodles (Chicken)',      (SELECT id FROM categories WHERE name = 'Chinese Food'), 280.0, FALSE, TRUE),
  ('Noodles (Veg)',          (SELECT id FROM categories WHERE name = 'Chinese Food'), 220.0, TRUE,  TRUE);

-- SPECIAL FOOD (category 16)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Neram Special Fried Chicken Combo',  (SELECT id FROM categories WHERE name = 'Special Food'), 499.0, FALSE, TRUE),
  ('Mexican Rice Served with Salsa',     (SELECT id FROM categories WHERE name = 'Special Food'), 270.0, TRUE,  TRUE);

-- SHRIMP & PRAWNS VARIETIES (category 17)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Golden Fried Shrimps',                  (SELECT id FROM categories WHERE name = 'Shrimp & Prawns Varieties'), 410.0, FALSE, TRUE),
  ('Neram Special Butter Garlic Prawns',    (SELECT id FROM categories WHERE name = 'Shrimp & Prawns Varieties'), 480.0, FALSE, TRUE),
  ('Dynamite Shrimps',                      (SELECT id FROM categories WHERE name = 'Shrimp & Prawns Varieties'), 460.0, FALSE, TRUE),
  ('Prawns Tempura',                        (SELECT id FROM categories WHERE name = 'Shrimp & Prawns Varieties'), 440.0, FALSE, TRUE),
  ('Szechuan Chilli Prawns',                (SELECT id FROM categories WHERE name = 'Shrimp & Prawns Varieties'), 430.0, FALSE, TRUE);

-- HIGH-PROTEIN & GYM BOWLS (category 18)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Grilled Chicken & Quinoa Energy Bowl',   (SELECT id FROM categories WHERE name = 'High-Protein & Gym Bowls'), 390.0, FALSE, TRUE),
  ('Egg White & Avocado Power Salad',        (SELECT id FROM categories WHERE name = 'High-Protein & Gym Bowls'), 330.0, FALSE, TRUE),
  ('Tofu & Brown Rice Teriyaki Bowl',        (SELECT id FROM categories WHERE name = 'High-Protein & Gym Bowls'), 340.0, TRUE,  TRUE),
  ('Mexican Black Bean & Chicken Salad',     (SELECT id FROM categories WHERE name = 'High-Protein & Gym Bowls'), 350.0, FALSE, TRUE);

-- SIDE DISHES (category 19)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('French Fries',      (SELECT id FROM categories WHERE name = 'Side Dishes'), 160.0, TRUE, TRUE),
  ('Potato Wedges',     (SELECT id FROM categories WHERE name = 'Side Dishes'), 180.0, TRUE, TRUE),
  ('Mashed Potato',     (SELECT id FROM categories WHERE name = 'Side Dishes'), 180.0, TRUE, TRUE);

-- LOADED FRIES (category 20)
INSERT INTO menu_items (name, category_id, price, is_veg, is_available) VALUES
  ('Cheesy Bacon Loaded Fries',                          (SELECT id FROM categories WHERE name = 'Loaded Fries'), 340.0, FALSE, TRUE),
  ('Mexican Taco & Salsa Loaded Fries',                  (SELECT id FROM categories WHERE name = 'Loaded Fries'), 320.0, FALSE, TRUE),
  ('BBQ Pulled Chicken Loaded Fries',                    (SELECT id FROM categories WHERE name = 'Loaded Fries'), 330.0, FALSE, TRUE),
  ('Buffalo Chicken & Ranch Loaded Fries',               (SELECT id FROM categories WHERE name = 'Loaded Fries'), 330.0, FALSE, TRUE),
  ('Neram Special Philly Cheesesteak Loaded Fries',      (SELECT id FROM categories WHERE name = 'Loaded Fries'), 410.0, FALSE, TRUE),
  ('Chilli Cheese Loaded Fries',                         (SELECT id FROM categories WHERE name = 'Loaded Fries'), 250.0, TRUE,  TRUE),
  ('Peri-Peri Garlic Cheese Fries',                      (SELECT id FROM categories WHERE name = 'Loaded Fries'), 250.0, TRUE,  TRUE),
  ('Greek Feta & Herb Loaded Fries',                     (SELECT id FROM categories WHERE name = 'Loaded Fries'), 280.0, TRUE,  TRUE);

-- RICE MEALS (category 21)
-- Pricing format Q-26/H-500/F-900 stored in description; price set to 0 for POS selection
INSERT INTO menu_items (name, category_id, price, description, is_veg, is_available) VALUES
  ('Moroccan Chicken with Rice',  (SELECT id FROM categories WHERE name = 'Rice Meals'), 0.0, 'Quarter: ₹260 / Half: ₹500 / Full: ₹900', FALSE, TRUE),
  ('Grilled Chicken with Rice',   (SELECT id FROM categories WHERE name = 'Rice Meals'), 0.0, 'Quarter: ₹260 / Half: ₹500 / Full: ₹900', FALSE, TRUE),
  ('Mexican Chicken with Rice',   (SELECT id FROM categories WHERE name = 'Rice Meals'), 0.0, 'Quarter: ₹260 / Half: ₹500 / Full: ₹900', FALSE, TRUE),
  ('Drakush with Rice',           (SELECT id FROM categories WHERE name = 'Rice Meals'), 0.0, 'Quarter: ₹260 / Half: ₹500 / Full: ₹900', FALSE, TRUE);

-- Sync sequences to current max
SELECT setval('categories_id_seq', (SELECT COALESCE(MAX(id), 1) FROM categories));
SELECT setval('menu_items_id_seq', (SELECT COALESCE(MAX(id), 1) FROM menu_items));
