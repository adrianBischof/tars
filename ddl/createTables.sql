-- Akka Event Sourcing Tables
CREATE TABLE IF NOT EXISTS public.event_journal (
  ordering BIGSERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,

  writer VARCHAR(255) NOT NULL,
  write_timestamp BIGINT,
  adapter_manifest VARCHAR(255),

  event_ser_id INTEGER NOT NULL,
  event_ser_manifest VARCHAR(255) NOT NULL,
  event_payload BYTEA NOT NULL,

  meta_ser_id INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload BYTEA,

  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX event_journal_ordering_idx ON public.event_journal(ordering);

CREATE TABLE IF NOT EXISTS public.event_tag(
    event_id BIGINT,
    persistence_id VARCHAR(255),
    sequence_number BIGINT,
    tag VARCHAR(256),
    PRIMARY KEY(persistence_id, sequence_number, tag),
    CONSTRAINT fk_event_journal
      FOREIGN KEY(persistence_id, sequence_number)
      REFERENCES event_journal(persistence_id, sequence_number)
      ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,

  snapshot_ser_id INTEGER NOT NULL,
  snapshot_ser_manifest VARCHAR(255) NOT NULL,
  snapshot_payload BYTEA NOT NULL,

  meta_ser_id INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload BYTEA,

  PRIMARY KEY(persistence_id, sequence_number)
);

-- Akka Projection Tables

CREATE TABLE IF NOT EXISTS akka_projection_offset_store (
  projection_name VARCHAR(255) NOT NULL,
  projection_key VARCHAR(255) NOT NULL,
  current_offset VARCHAR(255) NOT NULL,
  manifest VARCHAR(4) NOT NULL,
  mergeable BOOLEAN NOT NULL,
  last_updated BIGINT NOT NULL,
  PRIMARY KEY(projection_name, projection_key)
);

CREATE INDEX IF NOT EXISTS akka_projection_name_index ON akka_projection_offset_store (projection_name);

CREATE TABLE IF NOT EXISTS akka_projection_management (
  projection_name VARCHAR(255) NOT NULL,
  projection_key VARCHAR(255) NOT NULL,
  paused BOOLEAN NOT NULL,
  last_updated BIGINT NOT NULL,
  PRIMARY KEY(projection_name, projection_key)
);