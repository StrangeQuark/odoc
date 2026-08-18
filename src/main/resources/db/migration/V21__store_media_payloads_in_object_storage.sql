alter table media_assets
    add column object_key varchar(512),
    add column storage_state varchar(24) not null default 'AVAILABLE',
    add column content_sha256 varchar(64);

alter table media_assets
    alter column content drop not null;

alter table media_assets
    add constraint media_assets_storage_state_check
        check (storage_state in ('AVAILABLE', 'DELETE_PENDING'));

alter table media_assets
    add constraint media_assets_payload_location_check
        check (content is not null or object_key is not null);

create unique index media_assets_object_key_unique
    on media_assets (object_key)
    where object_key is not null;
