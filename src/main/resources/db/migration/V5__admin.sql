create table impersonation_audit_log (
    id                      uuid primary key,
    super_admin_user_id    uuid not null references user_accounts (id),
    target_user_id          uuid not null references user_accounts (id),
    target_pharmacy_id      uuid references pharmacies (id),
    reason                  varchar(500),
    started_at              timestamptz not null
);

create index idx_impersonation_audit_log_super_admin on impersonation_audit_log (super_admin_user_id, started_at desc);
