-- =====================================================================
-- 8. Official lab groups from the lecturer's list (Mobile_application_Group_List.xlsx)
--    14 groups (G01-G14), max 16 members each. Safe to run more than once.
--    Run the WHOLE file: TiDB -> Ctrl+A then Run; phpMyAdmin -> SQL tab -> Go.
-- =====================================================================
USE ict361_lab;

-- 8a. Groups G01-G14 (existing G01-G04 are kept; new ones get IDs 5-14)
INSERT IGNORE INTO labgroup (lab_group_id, group_name) VALUES
(1,'G01'),(2,'G02'),(3,'G03'),(4,'G04'),(5,'G05'),(6,'G06'),(7,'G07'),
(8,'G08'),(9,'G09'),(10,'G10'),(11,'G11'),(12,'G12'),(13,'G13'),(14,'G14');
-- fallback: if an ID was already taken, still make sure every group name exists
INSERT IGNORE INTO labgroup (group_name) VALUES
('G01'),('G02'),('G03'),('G04'),('G05'),('G06'),('G07'),('G08'),('G09'),('G10'),('G11'),('G12'),('G13'),('G14');

-- 8b. Programme used in the list that did not exist yet
INSERT IGNORE INTO program (program_id, program_name) VALUES (4, 'ICT with Education');
INSERT IGNORE INTO program (program_name) VALUES ('ICT with Education');

-- 8c. The official list (student number, name, programme, group)
DROP TABLE IF EXISTS import_roster;
CREATE TABLE import_roster (
  student_number VARCHAR(20)  PRIMARY KEY,
  student_name   VARCHAR(100) NOT NULL,
  program_name   VARCHAR(100) NOT NULL,
  group_name     VARCHAR(20)  NOT NULL
);
INSERT INTO import_roster (student_number, student_name, program_name, group_name) VALUES
('202402387', 'Mulemwa Muchima', 'Computer Science', 'G01'),
('202406643', 'Edwin Kampamba', 'Computer Science', 'G01'),
('202400442', 'Ivwananji Chipasha', 'Computer Science', 'G01'),
('202305208', 'Alinani Sinyangwe', 'Computer Science', 'G01'),
('202408399', 'Grace Chilufya', 'Computer Science', 'G01'),
('202402615', 'Charity Namangolwa', 'Computer Science', 'G01'),
('202404528', 'Memory Mutemwa', 'Computer Science', 'G01'),
('202409048', 'Mwila Siaciti', 'Computer Science', 'G01'),
('202402859', 'Hope Nsana', 'Computer Science', 'G01'),
('202403063', 'Judith Mushabati', 'Computer Science', 'G01'),
('202406887', 'Mcnaldo Chilanga', 'Computer Science', 'G01'),
('202403266', 'Collins Kalela', 'Computer Science', 'G01'),
('202409163', 'Nancy Mailoni', 'Computer Science', 'G01'),
('202408720', 'Onester Kalunga', 'Computer Science', 'G01'),
('202409779', 'Mayamiko Phiri', 'Computer Science', 'G01'),
('202408882', 'Atupele Andende', 'Computer Science', 'G01'),
('202400549', 'Joseph Chiwuswa', 'Computer Science', 'G02'),
('202400526', 'Maluba B Akakandelwa', 'Computer Science', 'G02'),
('202403134', 'Emmanuel Viazi', 'Computer Science', 'G02'),
('202408701', 'Memory Tembo', 'Computer Science', 'G02'),
('202401737', 'Salome Sitwala', 'Computer Science', 'G02'),
('202405690', 'Mwansa Mubanga', 'Computer Science', 'G02'),
('202404540', 'Paul Munene', 'Computer Science', 'G02'),
('202408808', 'Blessings Sinkamba', 'Computer Science', 'G02'),
('202308808', 'Joshua Chibale', 'Computer Science', 'G02'),
('202408439', 'Blessings Tembo', 'Computer Science', 'G02'),
('202402633', 'Mwaka Hamilemba', 'Computer Science', 'G02'),
('202408554', 'Luumbi Malambo', 'Computer Science', 'G02'),
('202406948', 'Liseli Faustina Kalowa', 'Computer Science', 'G02'),
('202407170', 'Lute Walusungu Gondwe', 'Computer Science', 'G02'),
('202405590', 'Matildah Yusiku', 'Computer Science', 'G02'),
('202404194', 'Gift Chabuka', 'Computer Science', 'G03'),
('202307232', 'Ruth Kangwa', 'Computer Science', 'G03'),
('202406919', 'Jason Mbao', 'Computer Science', 'G03'),
('202404164', 'Chiko Mutale', 'Computer Science', 'G03'),
('202407862', 'Fabian Muloongo', 'Computer Science', 'G03'),
('202402069', 'Richard Mwansa', 'Computer Science', 'G03'),
('202410371', 'Memory Mwela', 'Computer Science', 'G03'),
('202406543', 'Gillian Mpola', 'Information Technology', 'G03'),
('201901101', 'Christabel Chikomona', 'ICT with Education', 'G03'),
('202301795', 'Steven Zulu', 'Computer Science', 'G03'),
('202409282', 'Kapunza Gwenani', 'Computer Science', 'G03'),
('202408164', 'Lenganji Simwanza', 'Computer Science', 'G03'),
('202403669', 'Eustane Chulu', 'Computer Science', 'G03'),
('202401965', 'Erastas Michelo', 'Computer Science', 'G03'),
('202405342', 'Manda Dickson', 'Computer Science', 'G04'),
('202405228', 'Nemar Kanyondwi', 'Computer Science', 'G04'),
('202402427', 'Michael Mutale', 'Information Technology', 'G04'),
('202402839', 'Sikumbuzo Monde', 'Computer Science', 'G04'),
('202107598', 'Cleopatra Ngulube', 'Computer Science', 'G04'),
('202404130', 'Hellen Mwanza', 'Computer Science', 'G04'),
('202306274', 'Lloyd Bulaya', 'Computer Science', 'G04'),
('202407149', 'Gedan Simbeye', 'Computer Science', 'G04'),
('202408185', 'Muleba Mwashi', 'Computer Science', 'G04'),
('202402132', 'Nkonde R Zimba', 'Information Technology', 'G04'),
('202105977', 'Memory Sokontwe', 'ICT with Education', 'G04'),
('202408941', 'David Kanyama', 'Computer Science', 'G04'),
('202302576', 'Frackson Mkandawire', 'Information Technology', 'G04'),
('202203671', 'Andrew Moyo', 'Computer Science', 'G04'),
('202404135', 'Thabo Muliwana', 'Computer Science', 'G04'),
('202402261', 'Mwila Stephen', 'Computer Science', 'G04'),
('202406213', 'Simazuwo Brian', 'Computer Science', 'G05'),
('202406222', 'Sanalumba Wakun’oli', 'Computer Science', 'G05'),
('202406591', 'David Mbilima', 'Computer Science', 'G05'),
('202410059', 'Tembo Kunda', 'Computer Science', 'G05'),
('202407895', 'Chabu Chileleko', 'Computer Science', 'G05'),
('202400756', 'Nachinga Thelma', 'Computer Science', 'G05'),
('202406797', 'Kamwana Miriam', 'Computer Science', 'G05'),
('202409524', 'Mwelwa Maggie', 'Computer Science', 'G05'),
('202200670', 'Mshisha Dada', 'Computer Science', 'G05'),
('202409126', 'Mukangwa Brian', 'Computer Science', 'G05'),
('202408328', 'Shikupa Charles', 'Computer Science', 'G05'),
('202307112', 'Mwape Prisca', 'Computer Science', 'G05'),
('202400260', 'Shikabanga Cecilia', 'Computer Science', 'G05'),
('202403973', 'Nevers Mwaba', 'Computer Science', 'G05'),
('202403184', 'Febby Ng''anwe', 'Computer Science', 'G05'),
('202407184', 'Muyoba Mulako', 'Computer Science', 'G06'),
('202400975', 'Owen M. Mukela', 'Computer Science', 'G06'),
('202407403', 'Previous Mukosha', 'Computer Science', 'G06'),
('202400421', 'Immaculate Mwanangombe', 'Computer Science', 'G06'),
('202301165', 'Houghton Chibozyi', 'Computer Science', 'G06'),
('202402071', 'Winnia Mwanza', 'Computer Science', 'G06'),
('202400957', 'Bosswell Habenzu', 'Computer Science', 'G06'),
('202402043', 'Kamwemba Kulumuku', 'Computer Science', 'G06'),
('202409550', 'Wilfred Lungo', 'Computer Science', 'G06'),
('202405691', 'Chakuletela Musukwa', 'Computer Science', 'G06'),
('202403830', 'Charles Chisala', 'Computer Science', 'G06'),
('202407202', 'Namasiku Beauty Siemisa', 'Computer Science', 'G06'),
('202401889', 'Fridah Muzungu', 'Computer Science', 'G06'),
('202406520', 'Mundia Mundia', 'Computer Science', 'G06'),
('202408897', 'Marcus Malulu', 'Computer Science', 'G06'),
('202303351', 'Mwendalubi Mike Manyika', 'Computer Science', 'G07'),
('202406767', 'Bwalya Manyando', 'Computer Science', 'G07'),
('202404746', 'Rachael Shabolyo', 'Computer Science', 'G07'),
('202408031', 'Edwin Makuya', 'Computer Science', 'G07'),
('202408025', 'Cryvet Simfukwe', 'Computer Science', 'G07'),
('202404317', 'Yeshua Owen', 'Computer Science', 'G07'),
('202407565', 'Reagan Chisanga', 'Computer Science', 'G07'),
('202408138', 'Chanda Mubanga', 'Computer Science', 'G07'),
('202405883', 'Isaiah Mbangweta', 'Computer Science', 'G07'),
('202409469', 'Mapalo Mwape', 'Computer Science', 'G07'),
('202402434', 'Ngosa Florence', 'Computer Science', 'G07'),
('202403931', 'M''manga Modester', 'Computer Science', 'G07'),
('202400114', 'Kazembe Sing''ambwa', 'Computer Science', 'G07'),
('202404096', 'Temwini Phiri', 'Computer Science', 'G07'),
('202401761', 'Caleb Mukelabai Kalimina', 'Computer Science', 'G07'),
('202406501', 'Bwalya Marvelous', 'Computer Science', 'G08'),
('202408157', 'Soko Zilombo Choolwe', 'Computer Science', 'G08'),
('202408295', 'Mbewe Mathews', 'Computer Science', 'G08'),
('202404634', 'Sakala Blessings', 'Computer Science', 'G08'),
('202409794', 'Moyo Sibongile', 'Computer Science', 'G08'),
('202406011', 'Gumbo Dailess', 'Computer Science', 'G08'),
('202409214', 'Shaba Patience', 'Computer Science', 'G08'),
('202400397', 'Chishala Juliet', 'Computer Science', 'G08'),
('202407247', 'Chileshe Sampa', 'Computer Science', 'G08'),
('201802185', 'Kabengele Remmy', 'Computer Science', 'G08'),
('202402295', 'Miyambo Emmanuel', 'Information Technology', 'G08'),
('202106741', 'Kanjela Musenge', 'Computer Science', 'G08'),
('202406031', 'Florence Shimpa Musonda', 'Computer Science', 'G09'),
('202407879', 'Gerald Gondwe', 'Computer Science', 'G09'),
('202400653', 'Bernadette Chanda', 'Information Technology', 'G09'),
('202403271', 'William Simbeye', 'Computer Science', 'G09'),
('202402510', 'Derrick Kabonso', 'Computer Science', 'G09'),
('202405283', 'Miti Steven', 'Computer Science', 'G09'),
('202409051', 'Kampamba Charity', 'Computer Science', 'G09'),
('202403132', 'Benzu Himoonde', 'Computer Science', 'G09'),
('202405388', 'Frazer Macha', 'Computer Science', 'G09'),
('202408282', 'Timothy Malasha', 'Computer Science', 'G09'),
('202410672', 'Kalunga Mervis', 'Information Technology', 'G09'),
('202407064', 'Violet Ngoma', 'Computer Science', 'G09'),
('201701343', 'Mweene Simon', 'Computer Science', 'G09'),
('202406408', 'Chimbala Christopher', 'Computer Science', 'G09'),
('202406593', 'Enock Simumba', 'Computer Science', 'G09'),
('202406805', 'Eustuce Kasongo', 'Computer Science', 'G09'),
('202404114', 'Beatrice Chibesa', 'Computer Science', 'G10'),
('202400350', 'Angel Bwalya', 'Computer Science', 'G10'),
('202400550', 'Evans Saikolo', 'Computer Science', 'G10'),
('202402843', 'Titus Bwalya', 'Computer Science', 'G10'),
('202404621', 'Ezron Chiluba', 'Computer Science', 'G10'),
('202410470', 'Juliet Mulenga', 'Computer Science', 'G10'),
('202402030', 'Ilukena Mundia', 'Computer Science', 'G10'),
('202409245', 'Cascious Nyambe', 'Computer Science', 'G10'),
('202410007', 'Aquirina Mulauzi', 'Computer Science', 'G10'),
('202402824', 'Tobias Phiri', 'Information Technology', 'G10'),
('202410384', 'Mary Mula', 'Computer Science', 'G10'),
('202407468', 'Weston Mulenga', 'Information Technology', 'G10'),
('202404891', 'Christopher C.Chimfwembe', 'Computer Science', 'G10'),
('202405964', 'Choolwe Mweembe', 'Computer Science', 'G10'),
('202410253', 'Oscar Chipango', 'Computer Science', 'G10'),
('202109428', 'Gaidon Sichone', 'Computer Science', 'G10'),
('202407015', 'Miti Katanga', 'Computer Science', 'G11'),
('202404688', 'Mbasela Fabian', 'Computer Science', 'G11'),
('202404172', 'Chola Ben', 'Computer Science', 'G11'),
('202403019', 'Hamasukwa Agrippa C.', 'Computer Science', 'G11'),
('202401150', 'Muunda Mainza', 'Computer Science', 'G11'),
('202001699', 'Mumbi Chibesa', 'Computer Science', 'G11'),
('202403846', 'Chanda Collins', 'Computer Science', 'G11'),
('202405943', 'Chilufya Mapalo', 'Computer Science', 'G11'),
('202203897', 'Mwape Kelvin', 'Computer Science', 'G11'),
('202206607', 'Kansamba Auxiria', 'Computer Science', 'G11'),
('202305732', 'Banda Salima', 'Computer Science', 'G11'),
('202403844', 'Traore Lamin', 'Computer Science', 'G11'),
('202303375', 'Daka Racheal', 'Computer Science', 'G11'),
('202401321', 'Moon Clive', 'Information Technology', 'G12'),
('202404407', 'Zimba Emmanuel', 'Information Technology', 'G12'),
('202405095', 'Kaonga Memory', 'Computer Science', 'G12'),
('202405321', 'Mukupa Franscine', 'Computer Science', 'G12'),
('202409728', 'Nakanyika Nsungani', 'Computer Science', 'G12'),
('202406233', 'Chipampe Stanslous', 'Computer Science', 'G12'),
('202401475', 'Mukonta Davis', 'Computer Science', 'G12'),
('202406877', 'Chishala Alfred', 'Computer Science', 'G12'),
('202301116', 'Phiri Brian', 'Computer Science', 'G12'),
('202301173', 'Chola Simon', 'Computer Science', 'G12'),
('202300654', 'Ng''omba Kangwa', 'Computer Science', 'G12'),
('202404608', 'Kabutu Ethel', 'Information Technology', 'G12'),
('202409694', 'Mwenda Hamulambo Sostern', 'Computer Science', 'G12'),
('202404703', 'Masambu Kalenga', 'Computer Science', 'G12'),
('202408913', 'Silunji Victor', 'Computer Science', 'G13'),
('202404454', 'Sakala Daniel', 'Computer Science', 'G13'),
('202410021', 'Mawere Saidi', 'Computer Science', 'G13'),
('202408632', 'Mwanza Taizya', 'Computer Science', 'G13'),
('202406171', 'Mwitwa Clement', 'Computer Science', 'G13'),
('202408752', 'Banda Agness', 'Computer Science', 'G13'),
('202400961', 'N''gandwe Charles', 'Computer Science', 'G13'),
('202409714', 'M''soni Deborah', 'Computer Science', 'G13'),
('202402226', 'Ng''andu Lwendo', 'Computer Science', 'G13'),
('202400014', 'Chile Bwalya', 'Computer Science', 'G13'),
('202404480', 'Banda Mphaso', 'Computer Science', 'G13'),
('202406515', 'Tembo Patience', 'Computer Science', 'G13'),
('202407984', 'Mwambazi Grace', 'Computer Science', 'G13'),
('202401433', 'Ndhlovu Godfrey', 'Computer Science', 'G13'),
('202406496', 'Lungu Rebecca', 'Computer Science', 'G13'),
('202406168', 'Jumbe Thabo', 'Computer Science', 'G14'),
('202410082', 'Gondwe Joyce', 'Computer Science', 'G14'),
('202401120', 'Kapinga Antonette', 'Computer Science', 'G14'),
('202403000', 'Lungu Josephat', 'Computer Science', 'G14'),
('202407636', 'Namukanzye Foster', 'Computer Science', 'G14'),
('202308118', 'Sikubeka Emmanuel', 'Computer Science', 'G14'),
('202410125', 'Judge Ezekiel', 'Computer Science', 'G14'),
('202408068', 'Nduba Joel', 'Computer Science', 'G14'),
('202407198', 'Maseba Neo', 'Computer Science', 'G14'),
('202402976', 'Mapulanga Henry', 'Computer Science', 'G14'),
('202406795', 'Kalengo Andrew', 'Computer Science', 'G14'),
('202408447', 'Kamfwa Nathan', 'Computer Science', 'G14'),
('202406183', 'Chansa Sylvester', 'Computer Science', 'G14'),
('202301847', 'Njunga Emmanuel', 'Computer Science', 'G14');

-- 8d. Students NOT on the official list leave their group (Unassigned). Nobody is deleted.
UPDATE students
SET lab_group_id = NULL, version = version + 1
WHERE is_deleted = 0 AND lab_group_id IS NOT NULL
  AND student_number NOT IN (SELECT student_number FROM import_roster);

-- 8e. Students already in the database: move to their official group and programme, approve them.
--     (Their own registered name and login are kept.)
UPDATE students s
JOIN import_roster i ON i.student_number = s.student_number
JOIN labgroup g      ON g.group_name = i.group_name
JOIN program p       ON p.program_name = i.program_name
SET s.lab_group_id = g.lab_group_id,
    s.program_id   = p.program_id,
    s.approval_status = 'APPROVED',
    s.version = s.version + 1
WHERE s.is_deleted = 0;

-- 8f. New students: a locked placeholder account (nobody can log in with it),
--     claimed later when the student registers with the same number and name.
INSERT INTO accounts (username, password, email, role)
SELECT i.student_number,
       '$2b$10$3YsWJsO8pYWyCNuhrX4lXZnyKsH.onp4fTrJYtPN1Tx9G0GdaQFuk',
       CONCAT(i.student_number, '@pending.local'),
       'student'
FROM import_roster i
WHERE NOT EXISTS (SELECT 1 FROM students s WHERE s.student_number = i.student_number)
  AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.username = i.student_number);

INSERT INTO students (account_id, student_name, student_number, program_id, lab_group_id, approval_status)
SELECT a.account_id, i.student_name, i.student_number, p.program_id, g.lab_group_id, 'APPROVED'
FROM import_roster i
JOIN accounts a ON a.username = i.student_number
               AND a.role = 'student'
               AND a.email = CONCAT(i.student_number, '@pending.local')
JOIN program p  ON p.program_name = i.program_name
JOIN labgroup g ON g.group_name = i.group_name
WHERE NOT EXISTS (SELECT 1 FROM students s  WHERE s.student_number = i.student_number)
  AND NOT EXISTS (SELECT 1 FROM students s2 WHERE s2.account_id = a.account_id);

-- 8g. Check: members per group (must match the list, none above 16)
SELECT g.group_name, COUNT(s.student_id) AS members
FROM labgroup g
LEFT JOIN students s ON s.lab_group_id = g.lab_group_id AND s.is_deleted = 0
GROUP BY g.lab_group_id, g.group_name
ORDER BY g.group_name;

-- 8h. Check: anyone on the list who could NOT be imported (should return no rows)
SELECT i.* FROM import_roster i
LEFT JOIN students s ON s.student_number = i.student_number AND s.is_deleted = 0
WHERE s.student_id IS NULL;

-- 8i. When both checks look right, remove the helper table:
-- DROP TABLE import_roster;

-- 8j. Check: registered students (real email) who are NOT on the list and are now Unassigned.
--     Usually a typo in their student number - compare with the lecturer's list and fix in the app.
SELECT s.student_number, s.student_name
FROM students s JOIN accounts a ON a.account_id = s.account_id
WHERE s.is_deleted = 0 AND s.lab_group_id IS NULL AND a.email NOT LIKE '%@pending.local';
