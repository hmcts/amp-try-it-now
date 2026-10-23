-- One row per captured example, for every API this engine serves.
--
-- The body is `json`, deliberately, not `jsonb`. jsonb reparses and reserialises the document - it
-- normalises whitespace, reorders keys and drops duplicates - so a body read back from jsonb is not
-- the text that was validated and approved. Nothing here queries inside a body, so jsonb's indexing
-- buys nothing while its rewriting would undermine the one guarantee this table exists to provide.
create table recording (
    id                uuid        primary key,
    api_id            text        not null,   -- the registry short code: slc, rcc
    operation_id      text        not null,   -- getCourtScheduleByCaseUrn
    method            text        not null,   -- GET. Part of the key: one path can serve several
    request_key       text        not null,   -- the canonical request: path after the short code,
                                              -- plus sorted query, lower-cased
    http_status       int         not null,
    body              json        not null,
    response_headers  json,                   -- replayed verbatim. Location, ETag and friends are
                                              -- part of a contract, not decoration
    status            text        not null,   -- UNPUBLISHED | PUBLISHED | ARCHIVED
    scenario_id       text,                   -- set at publish, e.g. allocated. The selector for a
                                              -- request that cannot be keyed on its path alone
    summary           text,                   -- shown on GET /{shortCode}/scenarios
    recorded_from     text        not null,   -- dev | ste | sit, or 'fixture' for committed seeds
    recorded_at       timestamptz not null,
    recorded_by       text        not null,   -- the caller's Entra oid, from the validated token
    published_at      timestamptz,
    published_by      text
);

-- At most one PUBLISHED recording per request, per API. api_id leads: two APIs may legitimately
-- use the same URN text, and without it they would collide. A partial index rather than a plain
-- unique constraint, so superseded and unpublished rows accumulate against the same key while
-- only one of them is ever served.
create unique index recording_published_key_idx
    on recording (api_id, method, request_key)
    where status = 'PUBLISHED';

-- A scenario id is the alternative way in, so it must be unique per API where it is set.
create unique index recording_published_scenario_idx
    on recording (api_id, operation_id, scenario_id)
    where status = 'PUBLISHED' and scenario_id is not null;

create index recording_api_status_idx on recording (api_id, status);
