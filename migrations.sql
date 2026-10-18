-- =====================================================================
-- Nexa (ICT361) database migrations
-- Brings an existing ict361_lab database up to date with the current app:
--   * lecturer approvals  (students.approval_status)
--   * chat                (chat_messages, chat_keys, chat_reads)
--
-- HOW TO USE (run ONCE per database):
--   New database:      run schema.sql first, then this file.
--   Existing database: run only this file.
-- phpMyAdmin: click the ict361_lab database -> SQL tab -> paste -> Go.
--
-- Safe to re-run: tables use IF NOT EXISTS. If the ALTER in step 1 says
-- "Duplicate column name 'approval_status'", that step was already done:
-- skip it and run the rest.
-- =====================================================================

USE ict361_lab;

-- 1) Lecturer approvals ------------------------------------------------
-- Existing students default to APPROVED; new self-registrations are
-- inserted as PENDING by registerSafe.js.
ALTER TABLE students
  ADD COLUMN approval_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'APPROVED';

-- 2) Chat messages -----------------------------------------------------
--   Room messages:    room = 'uni' | 'prog:3' | 'grp:2' | 'staff:...', recipient_id = NULL
--   Private messages: room = NULL, recipient_id = the other person,
--                     body = encrypted JSON the server cannot read
CREATE TABLE IF NOT EXISTS chat_messages (
  message_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  room         VARCHAR(40) NULL,
  sender_id    INT NOT NULL,
  recipient_id INT NULL,
  body         TEXT NOT NULL,
  is_encrypted TINYINT(1) NOT NULL DEFAULT 0,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_chat_sender    FOREIGN KEY (sender_id)    REFERENCES accounts(account_id),
  CONSTRAINT fk_chat_recipient FOREIGN KEY (recipient_id) REFERENCES accounts(account_id),
  INDEX idx_chat_room (room, message_id),
  INDEX idx_chat_dm (sender_id, recipient_id, message_id)
) ENGINE=InnoDB;

-- 3) Public keys for end-to-end encrypted private messages -------------
-- The matching PRIVATE key never leaves the phone (Android Keystore).
CREATE TABLE IF NOT EXISTS chat_keys (
  account_id INT PRIMARY KEY,
  public_key TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_chat_key_account FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 4) Unread badges: last message each person has read per conversation --
--   conv_key = a room name ('uni', 'prog:3', ...) or 'dm:<other account_id>'
CREATE TABLE IF NOT EXISTS chat_reads (
  account_id   INT NOT NULL,
  conv_key     VARCHAR(40) NOT NULL,
  last_read_id BIGINT NOT NULL DEFAULT 0,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (account_id, conv_key),
  CONSTRAINT fk_chat_read_account FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5) Student requests: group change and student-number correction.
--    The student asks; only a lecturer's approval changes anything.
CREATE TABLE IF NOT EXISTS student_requests (
  request_id          INT AUTO_INCREMENT PRIMARY KEY,
  student_id          INT NOT NULL,
  type                ENUM('GROUP_CHANGE','NUMBER_CORRECTION') NOT NULL,
  requested_group_id  INT NULL,
  requested_number    VARCHAR(20) NULL,
  reason              VARCHAR(300) NULL,
  status              ENUM('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  lecturer_note       VARCHAR(300) NULL,
  decided_by          INT NULL,
  decided_at          DATETIME NULL,
  created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_req_student FOREIGN KEY (student_id)         REFERENCES students(student_id),
  CONSTRAINT fk_req_group   FOREIGN KEY (requested_group_id) REFERENCES labgroup(lab_group_id),
  CONSTRAINT fk_req_decider FOREIGN KEY (decided_by)         REFERENCES accounts(account_id),
  INDEX idx_req_status (status, type),
  INDEX idx_req_student (student_id, status)
) ENGINE=InnoDB;