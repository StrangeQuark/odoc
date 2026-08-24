ALTER TABLE pages ADD COLUMN author_id UUID REFERENCES user_accounts(id) ON DELETE SET NULL;

CREATE INDEX pages_author_id_idx ON pages (author_id);
