-- Reference catalogue data: canonical textbook editions only.
-- Contains no users, listings or activity. Safe to run repeatedly.

INSERT INTO books (id, title, author, publisher, isbn, class_level, education_board, subject, edition, language) VALUES
  ('b0000000-0000-4000-8000-000000000001', 'NCERT Mathematics', 'NCERT', 'NCERT', '9788174506207', 10, 'CBSE', 'Mathematics', '2024', 'English'),
  ('b0000000-0000-4000-8000-000000000002', 'NCERT Science', 'NCERT', 'NCERT', '9788174506214', 10, 'CBSE', 'Science', '2024', 'English'),
  ('b0000000-0000-4000-8000-000000000003', 'First Flight: English', 'NCERT', 'NCERT', '9788174506221', 10, 'CBSE', 'English', '2024', 'English'),
  ('b0000000-0000-4000-8000-000000000004', 'NCERT Physics Part I', 'NCERT', 'NCERT', '9789352531011', 12, 'CBSE', 'Physics', '2023', 'English'),
  ('b0000000-0000-4000-8000-000000000005', 'NCERT Chemistry Part I', 'NCERT', 'NCERT', '9789352531028', 12, 'CBSE', 'Chemistry', '2023', 'English'),
  ('b0000000-0000-4000-8000-000000000006', 'NCERT Biology', 'NCERT', 'NCERT', '9789352531035', 12, 'CBSE', 'Biology', '2023', 'English'),
  ('b0000000-0000-4000-8000-000000000007', 'ICSE Concise Mathematics', 'R. K. Bansal', 'Selina Publishers', '9788123456010', 9, 'ICSE', 'Mathematics', '2023', 'English'),
  ('b0000000-0000-4000-8000-000000000008', 'ICSE Concise Physics', 'S. S. Shome', 'Selina Publishers', '9788123456027', 9, 'ICSE', 'Physics', '2022', 'English'),
  ('b0000000-0000-4000-8000-000000000009', 'NCERT Social Science', 'NCERT', 'NCERT', '9788174502018', 9, 'CBSE', 'Social Science', '2024', 'English'),
  ('b0000000-0000-4000-8000-000000000010', 'Sparsh Hindi', 'NCERT', 'NCERT', '9788174502025', 10, 'CBSE', 'Hindi', '2024', 'Hindi'),
  ('b0000000-0000-4000-8000-000000000011', 'New Oxford Modern English', 'David Horsburgh', 'Oxford University Press', NULL, 8, 'ICSE', 'English', '2022', 'English'),
  ('b0000000-0000-4000-8000-000000000012', 'Cambridge IGCSE Biology', 'Mary Jones', 'Cambridge University Press', NULL, 9, 'OTHER', 'Biology', '2021', 'English')
ON CONFLICT DO NOTHING;
