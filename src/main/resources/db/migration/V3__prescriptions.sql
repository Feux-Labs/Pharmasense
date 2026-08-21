create table patients (
    id              uuid primary key,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint not null default 0,
    pharmacy_id     uuid not null references pharmacies (id),

    full_name       varchar(255) not null,
    phone_number    varchar(64),
    date_of_birth   date,
    notes           varchar(2000)
);

create index idx_patients_pharmacy_id on patients (pharmacy_id);

create table prescriptions (
    id                      uuid primary key,
    created_at              timestamptz not null,
    updated_at              timestamptz not null,
    version                 bigint not null default 0,
    pharmacy_id             uuid not null references pharmacies (id),

    patient_id              uuid not null references patients (id),
    prescribing_doctor      varchar(255),
    status                  varchar(32) not null default 'PENDING',
    notes                   varchar(2000),
    filled_by_user_id       uuid references user_accounts (id),
    filled_at               timestamptz
);

create index idx_prescriptions_pharmacy_status on prescriptions (pharmacy_id, status);
create index idx_prescriptions_patient_id on prescriptions (patient_id);

create table prescription_items (
    id                      uuid primary key,
    created_at              timestamptz not null,
    updated_at              timestamptz not null,
    version                 bigint not null default 0,
    pharmacy_id             uuid not null references pharmacies (id),

    prescription_id         uuid not null references prescriptions (id),
    inventory_item_id       uuid not null references inventory_items (id),
    quantity_prescribed     integer not null,
    quantity_filled         integer not null default 0,
    dosage_instructions     varchar(500)
);

create index idx_prescription_items_prescription_id on prescription_items (prescription_id);
