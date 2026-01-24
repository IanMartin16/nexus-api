alter table nexus_conversations
  add column if not exists summary text;

alter table nexus_conversations
  add column if not exists summary_updated_at timestamptz;
