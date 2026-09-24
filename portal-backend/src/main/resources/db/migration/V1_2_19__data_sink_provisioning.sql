-- Each sink records whether its storage physically exists, so a sink added after a release is known
-- to hold nothing yet. datasets.provisioned stays: it also covers sinks removed since the release,
-- whose resources only the dataset teardown drops.
alter table data_sinks
    add column provisioned boolean not null default false;

-- A sink added in DRAFT since the last release is marked too, because nothing tells it apart. That
-- asks for a confirmation once too often; the opposite reading would skip it for a table that
-- exists.
update data_sinks s
set provisioned = true
from datasets d
where d.id = s.dataset_id
  and d.provisioned;
