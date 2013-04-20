DROP TABLE items;
DROP TABLE keywords;

CREATE UNLOGGED TABLE items
(
  id serial,
  name character varying(1000),
  description character varying(10000)
)
WITH (
  OIDS=FALSE
);

CREATE TABLE keywords
(
  id serial NOT NULL,
  nameId bigint,
  name character varying(1000),
  keyword character varying(30),
  count integer
)
WITH (
  OIDS=FALSE
);

CREATE INDEX idx_keyword
  ON keywords
  USING hash
  (keyword COLLATE pg_catalog."default");

ALTER TABLE items OWNER TO postgres;
ALTER TABLE keywords OWNER TO postgres;
