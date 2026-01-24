-- V1__init.sql (PostgreSQL)

-- 1) Conversaciones (por sessionId + product)
create table if not exists nexus_conversations (
  id            uuid primary key,
  session_id    text not null,
  product       text not null,
  created_at    timestamptz not null default now(),
  last_seen_at  timestamptz not null default now()
);

create index if not exists idx_nexus_conv_session_product
  on nexus_conversations(session_id, product);

-- 2) Mensajes
create table if not exists nexus_messages (
  id              uuid primary key,
  conversation_id uuid not null references nexus_conversations(id) on delete cascade,
  role            text not null,         -- user | assistant | system
  content         text not null,
  created_at      timestamptz not null default now()
);

create index if not exists idx_nexus_msg_conv_created
  on nexus_messages(conversation_id, created_at);
