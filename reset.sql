DROP TABLE items;

CREATE UNLOGGED TABLE items
(
  id serial,
  name character varying(1000),
  description character varying(10000)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE items
  OWNER TO postgres;
