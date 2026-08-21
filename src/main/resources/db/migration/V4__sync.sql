-- sequence_number is a bigserial (NOT the primary key) purely to give the
-- offline-sync pull endpoint a monotonically increasing cursor - UUIDs
-- aren't sortable by creation order.
create table sync_change_log (
    id                  uuid primary key,
    pharmacy_id         uuid not null references pharmacies (id),
    entity_type         varchar(32) not null,
    entity_id           uuid not null,
    operation           varchar(16) not null,
    payload             text,
    sequence_number     bigserial,
    created_at          timestamptz not null
);

create index idx_sync_change_log_pharmacy_seq on sync_change_log (pharmacy_id, sequence_number);

create table sync_push_receipts (
    id                      uuid primary key,
    pharmacy_id             uuid not null references pharmacies (id),
    client_operation_id     uuid not null,
    status                  varchar(16) not null,
    applied_at              timestamptz not null,

    constraint uk_sync_push_receipts_pharmacy_client_op unique (pharmacy_id, client_operation_id)
);
