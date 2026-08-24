alter table pages
    add column plain_text text not null default '',
    add column archived_at timestamp with time zone;

update pages set plain_text = content where plain_text = '';

drop index if exists pages_search_document_idx;
alter table pages drop column search_document;
alter table pages
    add column search_document tsvector generated always as (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(plain_text, '')), 'B')
    ) stored;
create index pages_search_document_idx on pages using gin (search_document);
create index pages_active_space_updated_idx on pages (space_id, updated_at desc)
    where archived_at is null;
