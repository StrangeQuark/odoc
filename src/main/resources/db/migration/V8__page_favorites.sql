create table page_favorites (
    page_id uuid not null references pages(id) on delete cascade,
    username varchar(120) not null,
    created_at timestamp with time zone not null,
    primary key (page_id, username)
);
