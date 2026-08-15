alter table pages
    add column search_document tsvector generated always as (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
    ) stored;

create index pages_search_document_idx on pages using gin (search_document);
