-- Manual local bootstrap template. Do NOT run this file automatically.
-- Do NOT execute against any database other than a new knowflow_ai schema.
-- Replace placeholders before running in your own MySQL client.

CREATE DATABASE IF NOT EXISTS knowflow_ai
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '<KNOWFLOW_DB_USERNAME>'@'localhost'
    IDENTIFIED BY '<KNOWFLOW_DB_PASSWORD>';

GRANT ALL PRIVILEGES ON knowflow_ai.* TO '<KNOWFLOW_DB_USERNAME>'@'localhost';

FLUSH PRIVILEGES;
