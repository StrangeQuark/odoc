alter table pages
    add column parent_id uuid references pages(id) on delete set null;

create index pages_parent_id_idx on pages (parent_id);
