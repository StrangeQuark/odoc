create table page_versions (
    id uuid primary key,
    page_id uuid not null references pages(id) on delete cascade,
    version_number integer not null,
    title varchar(240) not null,
    content text not null default '',
    created_at timestamp with time zone not null,
    unique (page_id, version_number)
);

create index page_versions_page_id_version_idx on page_versions (page_id, version_number desc);
