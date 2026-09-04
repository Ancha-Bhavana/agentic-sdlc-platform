create table workflow_run (
    id uuid primary key,
    correlation_id varchar(80) not null unique,
    status varchar(40) not null,
    current_revision integer not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    entity_version bigint not null default 0
);

create table workflow_revision (
    workflow_id uuid not null references workflow_run(id),
    revision integer not null,
    requirement_hash varchar(64) not null,
    repository_hash varchar(64) not null,
    created_at timestamp with time zone not null,
    primary key (workflow_id, revision)
);

create table workflow_task (
    workflow_id uuid not null,
    workflow_revision integer not null,
    task_id varchar(100) not null,
    task_type varchar(50) not null,
    status varchar(30) not null,
    attempt integer not null default 0,
    lease_expires_at timestamp with time zone,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    entity_version bigint not null default 0,
    primary key (workflow_id, workflow_revision, task_id),
    foreign key (workflow_id, workflow_revision) references workflow_revision(workflow_id, revision)
);

create table workflow_task_dependency (
    workflow_id uuid not null,
    workflow_revision integer not null,
    task_id varchar(100) not null,
    dependency_task_id varchar(100) not null,
    primary key (workflow_id, workflow_revision, task_id, dependency_task_id),
    foreign key (workflow_id, workflow_revision, task_id)
        references workflow_task(workflow_id, workflow_revision, task_id),
    foreign key (workflow_id, workflow_revision, dependency_task_id)
        references workflow_task(workflow_id, workflow_revision, task_id)
);

create table context_artifact (
    id uuid primary key,
    workflow_id uuid not null,
    workflow_revision integer not null,
    artifact_key varchar(100) not null,
    artifact_version bigint not null,
    producer_task_id varchar(100) not null,
    schema_version varchar(30) not null,
    content_hash varchar(64) not null,
    input_hashes_json text not null,
    content_json text not null,
    created_at timestamp with time zone not null,
    unique (workflow_id, artifact_key, artifact_version),
    foreign key (workflow_id, workflow_revision) references workflow_revision(workflow_id, revision),
    foreign key (workflow_id, workflow_revision, producer_task_id)
        references workflow_task(workflow_id, workflow_revision, task_id)
);

create index idx_workflow_run_status on workflow_run(status);
create index idx_workflow_task_ready on workflow_task(status, lease_expires_at);
create index idx_context_artifact_lineage on context_artifact(workflow_id, workflow_revision, artifact_key);

