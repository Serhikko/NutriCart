-- Milestone 3: two-way sync and one account on every device.
--
-- The phone now PULLS: after draining its outbox it asks each synced table
-- for rows whose updated_at is past the last one it saw, and applies the ones
-- another client wrote (the website), or the tombstones the website set on
-- its own rows. These indexes make that "everything since" query cheap.
--
-- Nothing else changes in the schema. Signing in on the website with the
-- phone's account is an Auth setting, not a table: Authentication → Providers
-- → Email must be on, and the project's Site URL must be the website, so the
-- confirmation and magic-link emails land back on it (see supabase/README.md).

create index food_log_entries_updated_idx on food_log_entries (owner_id, updated_at);
create index water_entries_updated_idx on water_entries (owner_id, updated_at);
create index weight_entries_updated_idx on weight_entries (owner_id, updated_at);
