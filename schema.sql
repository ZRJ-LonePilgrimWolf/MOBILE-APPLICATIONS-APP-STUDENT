-- ICT361 lab schema. Matches the current backend code (auth.js, students.js, passwordReset.js)
-- and adds what the challenges need: version, is_deleted, operations (retry safety).
-- FAKE DATA ONLY.
CREATE DATABASE IF NOT EXISTS ict361_lab CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ict361_lab;

CREATE TABLE IF NOT EXISTS accounts (
  account_id INT AUTO_INCREMENT PRIMARY KEY,
  username   VARCHAR(100) NOT NULL UNIQUE,
  password   VARCHAR(255) NOT NULL,           -- bcrypt hash (code reads account.password)
  email      VARCHAR(150) NOT NULL,
  role       ENUM('student','lecturer') NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS program (
  program_id   INT AUTO_INCREMENT PRIMARY KEY,
  program_name VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS labgroup (
  lab_group_id INT AUTO_INCREMENT PRIMARY KEY,
  group_name   VARCHAR(20) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS students (
  student_id     INT AUTO_INCREMENT PRIMARY KEY,
  account_id     INT NOT NULL UNIQUE,
  student_name   VARCHAR(100) NOT NULL,
  student_number VARCHAR(20)  NOT NULL UNIQUE,   -- DB itself rejects duplicates
  program_id     INT NOT NULL,
  lab_group_id   INT NOT NULL,
  version        INT NOT NULL DEFAULT 1,         -- Challenge 3: conflict detection
  is_deleted     TINYINT(1) NOT NULL DEFAULT 0,  -- Challenge 3: soft delete
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (account_id)   REFERENCES accounts(account_id),
  FOREIGN KEY (program_id)   REFERENCES program(program_id),
  FOREIGN KEY (lab_group_id) REFERENCES labgroup(lab_group_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS password_reset_codes (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  account_id INT NOT NULL,
  reset_code VARCHAR(20) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Challenge 2: remembers each operation so a retry returns the SAME result instead of acting twice.
CREATE TABLE IF NOT EXISTS operations (
  operation_id  VARCHAR(64) PRIMARY KEY,
  account_id    INT NOT NULL,
  http_status   INT NOT NULL,
  response_body TEXT NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT IGNORE INTO program (program_name) VALUES ('Computer Science'),('Information Technology'),('Data Science');
INSERT IGNORE INTO labgroup (group_name) VALUES ('G01'),('G02'),('G03'),('G04');
-- Lecturer kept from your old export (same username + hash, so same password as before)
INSERT IGNORE INTO accounts (username, password, email, role) VALUES
 ('100000001','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','lecturer@example.com','lecturer');
