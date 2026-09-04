create table approval_decision (
    id uuid primary key,
    workflow_id uuid not null references workflow_run(id),
    workflow_revision integer not null,
    gate_type varchar(40) not null,
    artifact_hash varchar(64) not null,
    decision varchar(20) not null,
    actor varchar(200) not null,
    actor_role varchar(80) not null,
    reason varchar(1000) not null,
    valid boolean not null,
    decided_at timestamp with time zone not null,
    foreign key (workflow_id, workflow_revision) references workflow_revision(workflow_id, revision)
);

create table policy_result (
    id uuid primary key,
    workflow_id uuid,
    workflow_revision integer not null,
    policy_name varchar(100) not null,
    allowed boolean not null,
    reason varchar(1000) not null,
    evaluated_at timestamp with time zone not null
);

create table audit_event (
    id uuid primary key,
    workflow_id uuid,
    workflow_revision integer,
    correlation_id varchar(80) not null,
    event_type varchar(80) not null,
    actor varchar(200) not null,
    actor_role varchar(80) not null,
    payload_hash varchar(64) not null,
    details varchar(2000) not null,
    created_at timestamp with time zone not null
);

create index idx_approval_workflow_revision on approval_decision(workflow_id, workflow_revision, valid);
create index idx_policy_workflow_revision on policy_result(workflow_id, workflow_revision);
create index idx_audit_workflow_created on audit_event(workflow_id, created_at);
