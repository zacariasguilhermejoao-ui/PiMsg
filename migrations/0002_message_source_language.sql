-- Idioma em que o remetente escreveu a mensagem, usado para traducao automatica no dispositivo destinatario.
ALTER TABLE messages ADD COLUMN source_lang TEXT;
