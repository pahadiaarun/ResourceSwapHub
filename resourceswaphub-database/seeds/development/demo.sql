-- Development-only demo content. Never apply to production.
-- Demo password for all three accounts: BookBridge#2026
-- Requires database/seeds/reference/catalogue.sql to have been applied first.

INSERT INTO users (id, email, password_hash, display_name, city, locality, created_at, updated_at) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'aisha@bookbridge.demo',
   '$2a$12$cEP63sYVCHFL7OKouIkIwOtUk8qRac9si7KifGmOzswelM.2tZgZ2',
   'Aisha Kapoor', 'Pune', 'Kothrud', now() - interval '40 days', now() - interval '40 days'),
  ('a0000000-0000-4000-8000-000000000002', 'rohan@bookbridge.demo',
   '$2a$12$cEP63sYVCHFL7OKouIkIwOtUk8qRac9si7KifGmOzswelM.2tZgZ2',
   'Rohan Mehta', 'Pune', 'Baner', now() - interval '32 days', now() - interval '32 days'),
  ('a0000000-0000-4000-8000-000000000003', 'meera@bookbridge.demo',
   '$2a$12$cEP63sYVCHFL7OKouIkIwOtUk8qRac9si7KifGmOzswelM.2tZgZ2',
   'Meera Iyer', 'Bengaluru', 'Jayanagar', now() - interval '25 days', now() - interval '25 days')
ON CONFLICT DO NOTHING;

INSERT INTO collections (id, owner_id, title, description, city, locality, visibility, published_at, created_at, updated_at) VALUES
  ('c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Class 10 CBSE essentials', 'The full set I used last year — notes tucked into the maths book.', 'Pune', 'Kothrud',
   'PUBLIC', now() - interval '8 days', now() - interval '8 days', now() - interval '8 days'),
  ('c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000003',
   'Class 9 ICSE favourites', 'Two well-kept Selina books that made physics and maths click.', 'Bengaluru', 'Jayanagar',
   'PUBLIC', now() - interval '9 days', now() - interval '9 days', now() - interval '9 days')
ON CONFLICT DO NOTHING;

INSERT INTO listings (id, owner_id, book_id, category_id, condition, price_type, price, city, locality, notes, status, medium, created_at, updated_at) VALUES
  ('d0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   (SELECT id FROM books WHERE title = 'NCERT Mathematics' AND class_level = 10 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'GOOD', 'FIXED_PRICE', 250.00, 'Pune', 'Kothrud',
   'Cover has a small crease, pages are clean. A few helpful margin notes in pencil.', 'AVAILABLE', 'PHYSICAL',
   now() - interval '5 days', now() - interval '5 days'),
  ('d0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001',
   (SELECT id FROM books WHERE title = 'NCERT Science' AND class_level = 10 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'LIKE_NEW', 'FREE', NULL, 'Pune', 'Kothrud',
   'No longer needed — happy to pass it on to another student. Pickup from Kothrud.', 'AVAILABLE', 'PHYSICAL',
   now() - interval '4 days', now() - interval '4 days'),
  ('d0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000001',
   (SELECT id FROM books WHERE title = 'First Flight: English' AND class_level = 10 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'GOOD', 'NEGOTIABLE', 120.00, 'Pune', 'Kothrud',
   'First Flight reader. A couple of short notes in the first chapter.', 'AVAILABLE', 'PHYSICAL',
   now() - interval '3 days', now() - interval '3 days'),
  ('d0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000002',
   (SELECT id FROM books WHERE title = 'NCERT Social Science' AND class_level = 9 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'FAIR', 'FREE', NULL, 'Pune', 'Baner',
   'Old but complete. Free to a good home — collecting dust on my shelf.', 'AVAILABLE', 'PHYSICAL',
   now() - interval '2 days', now() - interval '2 days'),
  ('d0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000002',
   (SELECT id FROM books WHERE title = 'Sparsh Hindi' AND class_level = 10 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'LIKE_NEW', 'FIXED_PRICE', 150.00, 'Pune', 'Baner',
   'Sparsh Hindi textbook, barely opened.', 'AVAILABLE', 'PHYSICAL',
   now() - interval '1 day', now() - interval '1 day'),
  ('d0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000003',
   (SELECT id FROM books WHERE title = 'ICSE Concise Mathematics' AND class_level = 9 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'GOOD', 'NEGOTIABLE', NULL, 'Bengaluru', 'Jayanagar',
   'Ask me for a price — I would rather it go to a student than sit unused.', 'AVAILABLE', 'PHYSICAL',
   now() - interval '6 days', now() - interval '6 days'),
  ('d0000000-0000-4000-8000-000000000007', 'a0000000-0000-4000-8000-000000000003',
   (SELECT id FROM books WHERE title = 'ICSE Concise Physics' AND class_level = 9 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'GOOD', 'FIXED_PRICE', 180.00, 'Bengaluru', 'Jayanagar',
   'Concise Physics, clean inside. Covers have shelf wear.', 'AVAILABLE', 'PHYSICAL',
   now() - interval '7 days', now() - interval '7 days'),
  ('d0000000-0000-4000-8000-000000000008', 'a0000000-0000-4000-8000-000000000001',
   (SELECT id FROM books WHERE title = 'NCERT Physics Part I' AND class_level = 12 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'GOOD', 'FIXED_PRICE', 300.00, 'Pune', 'Kothrud',
   'Physics Part I, currently kept aside for a friend.', 'UNAVAILABLE', 'PHYSICAL',
   now() - interval '10 days', now() - interval '2 days'),
  ('d0000000-0000-4000-8000-000000000009', 'a0000000-0000-4000-8000-000000000002',
   (SELECT id FROM books WHERE title = 'NCERT Chemistry Part I' AND class_level = 12 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'FAIR', 'FREE', NULL, 'Pune', 'Baner',
   'This one has found a new home.', 'EXCHANGED', 'PHYSICAL',
   now() - interval '14 days', now() - interval '4 days'),
  ('d0000000-0000-4000-8000-000000000010', 'a0000000-0000-4000-8000-000000000003',
   (SELECT id FROM books WHERE title = 'NCERT Biology' AND class_level = 12 ORDER BY id LIMIT 1), (SELECT id FROM resource_categories WHERE slug = 'textbooks'),
   'GOOD', 'NEGOTIABLE', 200.00, 'Bengaluru', 'Jayanagar',
   'Previously listed Biology. I am keeping it for now.', 'WITHDRAWN', 'PHYSICAL',
   now() - interval '15 days', now() - interval '5 days')
ON CONFLICT DO NOTHING;

-- Membership lives in collection_items; listings.collection_id was dropped in V10.
INSERT INTO collection_items (collection_id, listing_id) VALUES
  ('c0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002'),
  ('c0000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000006'),
  ('c0000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000007')
ON CONFLICT (collection_id, listing_id) DO NOTHING;

INSERT INTO conversations (id, type, listing_id, direct_key, last_message_at, created_at) VALUES
  ('e0000000-0000-4000-8000-000000000001', 'DIRECT',
   'd0000000-0000-4000-8000-000000000001',
   'd0000000-0000-4000-8000-000000000001:a0000000-0000-4000-8000-000000000001:a0000000-0000-4000-8000-000000000002',
   now() - interval '20 hours', now() - interval '2 days'),
  ('e0000000-0000-4000-8000-000000000002', 'DIRECT',
   'd0000000-0000-4000-8000-000000000007',
   'd0000000-0000-4000-8000-000000000007:a0000000-0000-4000-8000-000000000002:a0000000-0000-4000-8000-000000000003',
   now() - interval '26 hours', now() - interval '3 days')
ON CONFLICT DO NOTHING;

INSERT INTO conversation_participants (conversation_id, user_id, role) VALUES
  ('e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'OWNER'),
  ('e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002', 'INTERESTED'),
  ('e0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000003', 'OWNER'),
  ('e0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002', 'INTERESTED')
ON CONFLICT DO NOTHING;

INSERT INTO messages (conversation_id, sender_id, body, created_at)
SELECT 'e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002',
       'Hi Aisha! Is the Class 10 maths book still available?', now() - interval '2 days'
WHERE NOT EXISTS (SELECT 1 FROM messages WHERE conversation_id = 'e0000000-0000-4000-8000-000000000001'
                  AND body = 'Hi Aisha! Is the Class 10 maths book still available?');

INSERT INTO messages (conversation_id, sender_id, body, created_at)
SELECT 'e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
       'Hi Rohan, yes it is! The pages are clean, just a little wear on the cover.', now() - interval '47 hours'
WHERE NOT EXISTS (SELECT 1 FROM messages WHERE conversation_id = 'e0000000-0000-4000-8000-000000000001'
                  AND body = 'Hi Rohan, yes it is! The pages are clean, just a little wear on the cover.');

INSERT INTO messages (conversation_id, sender_id, body, created_at)
SELECT 'e0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002',
       'That sounds perfect. Would you be able to hold it until Saturday?', now() - interval '20 hours'
WHERE NOT EXISTS (SELECT 1 FROM messages WHERE conversation_id = 'e0000000-0000-4000-8000-000000000001'
                  AND body = 'That sounds perfect. Would you be able to hold it until Saturday?');

INSERT INTO messages (conversation_id, sender_id, body, created_at)
SELECT 'e0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002',
       'Hi Meera, is the ICSE physics book negotiable?', now() - interval '3 days'
WHERE NOT EXISTS (SELECT 1 FROM messages WHERE conversation_id = 'e0000000-0000-4000-8000-000000000002'
                  AND body = 'Hi Meera, is the ICSE physics book negotiable?');

INSERT INTO messages (conversation_id, sender_id, body, created_at)
SELECT 'e0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000003',
       'Yes, happy to talk about the price. What did you have in mind?', now() - interval '26 hours'
WHERE NOT EXISTS (SELECT 1 FROM messages WHERE conversation_id = 'e0000000-0000-4000-8000-000000000002'
                  AND body = 'Yes, happy to talk about the price. What did you have in mind?');

UPDATE conversation_participants p
SET last_read_message_id = (
    SELECT max(m.id) FROM messages m WHERE m.conversation_id = p.conversation_id
)
WHERE p.user_id = 'a0000000-0000-4000-8000-000000000002';

UPDATE conversations c
SET last_message_at = (
    SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = c.id
)
WHERE c.id IN ('e0000000-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000002');
