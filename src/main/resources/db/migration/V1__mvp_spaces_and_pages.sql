CREATE TABLE spaces (
    id UUID PRIMARY KEY,
    space_key VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE pages (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces (id) ON DELETE CASCADE,
    title VARCHAR(240) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX pages_space_id_idx ON pages (space_id);
CREATE INDEX pages_title_idx ON pages (title);
