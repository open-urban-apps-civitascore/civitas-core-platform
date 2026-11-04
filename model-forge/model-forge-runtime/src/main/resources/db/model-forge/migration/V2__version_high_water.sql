-- The highest version ever assigned per logical URN. A pinned URN must always name the same
-- content, so a number stays taken after its version, or the whole artifact, is deleted. No
-- foreign key to artifact: the row has to outlive it.
set search_path to model_forge;

create table version_high_water (
    logical_urn text primary key,
    version     text not null
);

-- current_version is the highest version an artifact holds.
insert into version_high_water (logical_urn, version)
select logical_urn, current_version
  from artifact
 where current_version is not null;
