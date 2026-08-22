-- Renames the plan tier names to match the public pricing terms (FREE_PILOT
-- -> FREE, STARTER -> BASIC, GROWTH -> PRO) and adds what checkout/payment
-- tracking needs: a paid period end date, a complimentary-account flag, and
-- the payments table itself.

update pharmacies set plan = 'FREE' where plan = 'FREE_PILOT';
update pharmacies set plan = 'BASIC' where plan = 'STARTER';
update pharmacies set plan = 'PRO' where plan = 'GROWTH';

alter table pharmacies alter column plan set default 'FREE';

alter table pharmacies
    add column current_period_ends_at timestamptz,
    add column complimentary          boolean not null default false;

create table payments (
    id                       uuid primary key,
    pharmacy_id              uuid not null references pharmacies (id),
    target_plan              varchar(32) not null,
    amount_kobo              bigint not null,
    reference                varchar(128) not null unique,
    status                   varchar(16) not null default 'PENDING',
    paystack_transaction_id  varchar(64),
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    version                  bigint not null default 0
);

create index idx_payments_pharmacy_id on payments (pharmacy_id);
