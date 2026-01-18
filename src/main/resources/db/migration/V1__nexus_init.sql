create schema if not exists nexus;

create table if not exists nexus.doc_chunks (
  id uuid primary key,
  product text not null,
  source text not null,
  chunk_index int not null,
  chunk_text text not null,
  chunk_hash text not null,
  created_at timestamptz not null default now()
);

create unique index if not exists ux_doc_chunks_prod_hash
on nexus.doc_chunks(product, chunk_hash);
