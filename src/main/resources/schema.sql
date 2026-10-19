-- HR management system: MySQL 8.4 schema.
-- Safe to run more than once. Rules from DECISION.md (#19-#36) are enforced here as well as in the code.

CREATE TABLE IF NOT EXISTS employee (
  id BIGINT NOT NULL AUTO_INCREMENT,
  first_name VARCHAR(50) NOT NULL,
  last_name VARCHAR(50) NOT NULL,
  -- Unique. MySQL's default collation ignores case, so 'a1234567' and 'A1234567' clash.
  ic_passport VARCHAR(20) NOT NULL,
  role ENUM('HR', 'EMPLOYEE') NOT NULL,
  phone VARCHAR(30) NULL,
  email VARCHAR(100) NULL,
  address VARCHAR(255) NULL,
  -- Password material never leaves the server (decision #36).
  password_hash VARBINARY(64) NOT NULL,
  password_salt VARBINARY(32) NOT NULL,
  hash_iterations INT NOT NULL,
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_employee_ic_passport (ic_passport),
  CONSTRAINT ck_employee_iterations CHECK (hash_iterations > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS family_member (
  id BIGINT NOT NULL AUTO_INCREMENT,
  employee_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  relationship VARCHAR(50) NOT NULL,
  date_of_birth DATE NULL,
  PRIMARY KEY (id),
  KEY ix_family_employee (employee_id),
  CONSTRAINT fk_family_employee FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS leave_type (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  -- Days per year. NULL means uncapped (Unpaid).
  annual_allowance INT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_leave_type_name (name),
  CONSTRAINT ck_leave_type_allowance CHECK (annual_allowance IS NULL OR annual_allowance >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS leave_application (
  id BIGINT NOT NULL AUTO_INCREMENT,
  employee_id BIGINT NOT NULL,
  leave_type_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  days INT NOT NULL,
  reason VARCHAR(200) NULL,
  status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
  applied_at DATETIME NOT NULL,
  decided_by BIGINT NULL,
  decided_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY ix_leave_employee_start (employee_id, start_date),
  KEY ix_leave_status (status),
  CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
  CONSTRAINT fk_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_type (id),
  CONSTRAINT fk_leave_decider FOREIGN KEY (decided_by) REFERENCES employee (id),
  CONSTRAINT ck_leave_dates CHECK (end_date >= start_date),
  CONSTRAINT ck_leave_same_year CHECK (YEAR(start_date) = YEAR(end_date)),
  CONSTRAINT ck_leave_days CHECK (days = DATEDIFF(end_date, start_date) + 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Fixed ids so the rows are the same on every machine (decision #11).
INSERT IGNORE INTO leave_type (id, name, annual_allowance) VALUES
  (1, 'Annual', 14),
  (2, 'Medical', 14),
  (3, 'Emergency', 3),
  (4, 'Unpaid', NULL);
