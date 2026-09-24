create table if not exists public.galactic_users (
  username text primary key,
  salt text not null,
  hash text not null,
  token text not null
);

alter table public.galactic_users enable row level security;

-- The game server uses the Supabase service-role key from Render, so no public
-- client policy is needed. Android clients never receive the service-role key.
