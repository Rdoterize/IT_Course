CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL,
  number VARCHAR(80), cohort VARCHAR(40), department VARCHAR(150),
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS indicator (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(60) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  description TEXT
);
CREATE TABLE IF NOT EXISTS achievement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(250) NOT NULL,
  type VARCHAR(30) NOT NULL,
  level VARCHAR(30) NOT NULL,
  achieved_on DATE NOT NULL,
  description TEXT,
  details_json TEXT,
  status VARCHAR(20) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  FOREIGN KEY (owner_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS participant (
  achievement_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL,
  sort_order INT NOT NULL,
  PRIMARY KEY (achievement_id,user_id,role),
  FOREIGN KEY (achievement_id) REFERENCES achievement(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS achievement_indicator (
  achievement_id BIGINT NOT NULL,
  indicator_id BIGINT NOT NULL,
  PRIMARY KEY (achievement_id,indicator_id),
  FOREIGN KEY (achievement_id) REFERENCES achievement(id) ON DELETE CASCADE,
  FOREIGN KEY (indicator_id) REFERENCES indicator(id)
);
CREATE TABLE IF NOT EXISTS review_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  achievement_id BIGINT NOT NULL,
  reviewer_id BIGINT NOT NULL,
  approved BOOLEAN NOT NULL,
  comment TEXT,
  created_at TIMESTAMP NOT NULL,
  FOREIGN KEY (achievement_id) REFERENCES achievement(id) ON DELETE CASCADE,
  FOREIGN KEY (reviewer_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS attachment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  achievement_id BIGINT NOT NULL,
  original_name VARCHAR(240) NOT NULL,
  stored_name VARCHAR(80) NOT NULL,
  content_type VARCHAR(80) NOT NULL,
  size BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  FOREIGN KEY (achievement_id) REFERENCES achievement(id) ON DELETE CASCADE
);
