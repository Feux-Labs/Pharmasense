create table inventory_items (
    id                          uuid primary key,
    created_at                  timestamptz not null,
    updated_at                  timestamptz not null,
    version                     bigint not null default 0,
    pharmacy_id                 uuid not null references pharmacies (id),

    name                        varchar(255) not null,
    generic_name                varchar(255),
    category                    varchar(128),
    sku                         varchar(128),
    manufacturer                varchar(255),
    unit                        varchar(32) not null default 'unit',
    unit_selling_price          numeric(12, 2) not null,
    requires_prescription       boolean not null default false,
    low_stock_threshold         integer,
    qr_code                     varchar(64),
    active                      boolean not null default true,

    constraint uk_inventory_items_qr_code unique (qr_code)
);

create index idx_inventory_items_pharmacy_id on inventory_items (pharmacy_id);
create index idx_inventory_items_pharmacy_id_active on inventory_items (pharmacy_id, active);

create table inventory_batches (
    id                  uuid primary key,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    version             bigint not null default 0,
    pharmacy_id         uuid not null references pharmacies (id),

    inventory_item_id   uuid not null references inventory_items (id),
    batch_number        varchar(128) not null,
    quantity_on_hand    integer not null default 0,
    unit_cost_price     numeric(12, 2),
    expiry_date         date,
    received_at         date not null,
    qr_code             varchar(64),

    constraint uk_inventory_batches_qr_code unique (qr_code)
);

create index idx_inventory_batches_item_id on inventory_batches (inventory_item_id);
create index idx_inventory_batches_pharmacy_expiry on inventory_batches (pharmacy_id, expiry_date);

create table stock_movements (
    id                      uuid primary key,
    created_at              timestamptz not null,
    updated_at              timestamptz not null,
    version                 bigint not null default 0,
    pharmacy_id             uuid not null references pharmacies (id),

    inventory_batch_id      uuid not null references inventory_batches (id),
    inventory_item_id       uuid not null references inventory_items (id),
    movement_type           varchar(32) not null,
    quantity_delta          integer not null,
    reason                  varchar(500),
    performed_by_user_id    uuid not null references user_accounts (id)
);

create index idx_stock_movements_item_id on stock_movements (inventory_item_id, created_at desc);
create index idx_stock_movements_pharmacy_created on stock_movements (pharmacy_id, created_at);
