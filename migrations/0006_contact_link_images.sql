-- Permite associar uma imagem personalizada a cada link de contacto.
ALTER TABLE contact_links ADD COLUMN image_media_id TEXT REFERENCES media(id) ON DELETE SET NULL;
