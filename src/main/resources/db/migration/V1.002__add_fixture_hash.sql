-- The content hash of the committed fixture a row was seeded from.
--
-- Lets a deployment tell "this fixture is unchanged" from "this fixture was edited" without
-- comparing bodies, so seeding is idempotent when nothing moved and supersedes exactly when it did.
--
-- Null for anything not seeded from a fixture - a recording ingested through the admin API has no
-- committed source, and must never be superseded by one.
alter table recording add column fixture_hash text;
