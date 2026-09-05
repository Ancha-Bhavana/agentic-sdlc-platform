create table short_url (
    id uuid primary key,
    short_code varchar(12) not null unique,
    target_url varchar(2048) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    active boolean not null,
    redirect_count bigint not null default 0,
    entity_version bigint not null default 0
);

create table redirect_event (
    id uuid primary key,
    short_url_id uuid not null references short_url(id),
    occurred_at timestamp with time zone not null
);

create index idx_short_url_code_active on short_url(short_code, active);
create index idx_redirect_event_url_time on redirect_event(short_url_id, occurred_at);
