create table javadoc_snapshots (
    id uuid primary key,
    repository_binding_id uuid not null references repository_bindings(id) on delete cascade,
    source_path varchar(500) not null,
    package_name varchar(500) not null default '',
    type_name varchar(240) not null,
    type_kind varchar(32) not null,
    documentation text not null default '',
    members_json text not null default '[]',
    refreshed_at timestamptz not null,
    constraint javadoc_snapshots_repository_path_unique unique (repository_binding_id, source_path)
);

create index javadoc_snapshots_repository_id_idx on javadoc_snapshots (repository_binding_id, type_name);
