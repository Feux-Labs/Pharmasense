-- Password-based sign-in as an alternative to the email+OTP flow. Nullable
-- because Google-OAuth accounts never set one, and existing OTP-only rows
-- predate this column.
ALTER TABLE user_accounts
    ADD COLUMN password_hash VARCHAR(255);
