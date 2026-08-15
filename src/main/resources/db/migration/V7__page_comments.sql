create table page_comments (
    id uuid primary key,
    page_id uuid not null references pages(id) on delete cascade,
    parent_id uuid references page_comments(id) on delete cascade,
    author varchar(120) not null,
    body text not null,
    created_at timestamp with time zone not null
);

create index page_comments_page_id_created_at_idx on page_comments (page_id, created_at);
