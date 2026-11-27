-- Store the user's profile cover in the same R2 media system as their avatar.
ALTER TABLE users ADD COLUMN profile_cover_key TEXT;
