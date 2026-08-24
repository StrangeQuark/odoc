alter table media_assets drop constraint media_assets_storage_state_check;

alter table media_assets
    add constraint media_assets_storage_state_check
        check (storage_state in ('AVAILABLE', 'QUARANTINED', 'DELETE_PENDING'));
