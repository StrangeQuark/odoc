create table repository_bindings (
    id uuid primary key,
    space_id uuid not null references spaces(id) on delete cascade,
    github_url varchar(500) not null,
    owner varchar(120) not null,
    repository_name varchar(120) not null,
    description text not null default '',
    default_branch varchar(255) not null default '',
    stars integer not null default 0,
    readme_content text not null default '',
    readme_path varchar(500) not null default '',
    synced_at timestamptz not null,
    constraint repository_bindings_space_url_unique unique (space_id, github_url)
);

create index repository_bindings_space_id_idx on repository_bindings(space_id);
