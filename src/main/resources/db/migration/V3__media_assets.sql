create table media_assets (
    id uuid primary key,
    space_id uuid not null references spaces(id) on delete cascade,
    filename varchar(255) not null,
    content_type varchar(100) not null,
    content bytea not null,
    size_bytes bigint not null,
    created_at timestamptz not null
);

create index media_assets_space_id_idx on media_assets(space_id);
