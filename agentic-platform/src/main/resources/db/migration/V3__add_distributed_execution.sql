alter table workflow_task add column lease_owner varchar(160);
alter table workflow_task add column lease_token bigint not null default 0;
create index idx_workflow_task_lease_owner on workflow_task(lease_owner, lease_expires_at);

create table workflow_execution_spec (
    workflow_id uuid not null,
    workflow_revision integer not null,
    scenario_type varchar(30) not null,
    requirement_text text not null,
    repository_path text not null,
    correlation_id varchar(80) not null,
    created_at timestamp with time zone not null,
    primary key (workflow_id, workflow_revision),
    foreign key (workflow_id, workflow_revision) references workflow_revision(workflow_id, revision)
);
