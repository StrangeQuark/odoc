ALTER TABLE page_comments
    ADD COLUMN author_id UUID REFERENCES user_accounts(id) ON DELETE SET NULL;

CREATE INDEX page_comments_author_id_idx ON page_comments (author_id);
