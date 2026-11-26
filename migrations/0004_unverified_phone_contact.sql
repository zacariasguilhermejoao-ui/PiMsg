-- Unverified phone input is contact metadata, never a verified login credential.
ALTER TABLE users ADD COLUMN phone_contact TEXT;
