# CHECK BEFORE
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global'
  AND id.id_value LIKE '%gnd%';

UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)gnd/(.*)','\2'), id_namespace_fk = idns2.id
FROM identifier_namespace AS idns1, identifier_namespace AS idns2
WHERE id.id_value LIKE '%d-nb.info/gnd/%'
  AND idns1.idns_value LIKE 'global'
  AND id.id_namespace_fk = idns1.id
  AND idns2.idns_value LIKE 'gnd-id';

# CHECK AFTER
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'gnd-id';



# CHECK BEFORE
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global'
  AND id.id_value LIKE '%dbpedia.org/resource/%';

UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)dbpedia.org/resource/(.*)','\2'), id_namespace_fk = idns2.id
FROM identifier_namespace AS idns1, identifier_namespace AS idns2
WHERE id.id_value LIKE '%dbpedia.org/resource/%'
  AND idns1.idns_value LIKE 'global'
  AND id.id_namespace_fk = idns1.id
  AND idns2.idns_value LIKE 'dbpedia';

# CHECK AFTER
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'dbpedia';

# CHECK AFTER FOR ORGS
SELECT COUNT (kbc.kbc_id) FROM kbcomponent AS kbc, combo AS com, identifier AS id, identifier_namespace AS idns
WHERE com.combo_from_fk = kbc.kbc_id
  AND com.combo_to_fk = id.kbc_id
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'dbpedia';



# CHECK BEFORE
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global'
  AND id.id_value LIKE '%id.loc.gov/authorities/names/%';
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'loc';

UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)id.loc.gov/authorities/names/(.*)','\2'), id_namespace_fk = idns2.id
FROM identifier_namespace AS idns1, identifier_namespace AS idns2
WHERE id.id_value LIKE '%id.loc.gov/authorities/names/%'
  AND idns1.idns_value LIKE 'global'
  AND id.id_namespace_fk = idns1.id
  AND idns2.idns_value LIKE 'loc';

# CHECK AFTER
SELECT *
FROM org AS kbc, combo AS com, identifier AS id, identifier_namespace AS idns
WHERE com.combo_from_fk = kbc.kbc_id
  AND com.combo_to_fk = id.kbc_id
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'loc';
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'loc';




# CHECK BEFORE
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global'
  AND id.id_value LIKE '%www.lib.ncsu.edu/ld/onld/%';
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'ncsu';

UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)www.lib.ncsu.edu/ld/onld/(.*)','\2'), id_namespace_fk = idns2.id
FROM identifier_namespace AS idns1, identifier_namespace AS idns2
WHERE id.id_value LIKE '%www.lib.ncsu.edu/ld/onld/%'
  AND idns1.idns_value LIKE 'global'
  AND id.id_namespace_fk = idns1.id
  AND idns2.idns_value LIKE 'ncsu';

# CHECK AFTER
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'ncsu';



# CHECK BEFORE
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global'
  AND id.id_value LIKE '%isni-url.oclc.nl/isni/%';

UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)isni-url.oclc.nl/isni/(.*)','\2'), id_namespace_fk = idns2.id
FROM identifier_namespace AS idns1, identifier_namespace AS idns2
WHERE id.id_value LIKE '%isni-url.oclc.nl/isni/%'
  AND idns1.idns_value LIKE 'global'
  AND id.id_namespace_fk = idns1.id
  AND idns2.idns_value LIKE 'isni';

# CHECK AFTER
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'isni';



# CHECK BEFORE
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global'
  AND id.id_value LIKE '%viaf.org/viaf/%';

UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)viaf.org/viaf/(.*)','\2'), id_namespace_fk = idns2.id
FROM identifier_namespace AS idns1, identifier_namespace AS idns2
WHERE id.id_value LIKE '%viaf.org/viaf/%'
  AND idns1.idns_value LIKE 'global'
  AND id.id_namespace_fk = idns1.id
  AND idns2.idns_value LIKE 'viaf';

# CHECK AFTER
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'viaf';



# CHECK BEFORE
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global'
  AND id.id_value LIKE '%www.wikidata.org/wiki/%';

UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)www.wikidata.org/wiki/(.*)','\2'), id_namespace_fk = idns2.id
FROM identifier_namespace AS idns1, identifier_namespace AS idns2
WHERE id.id_value LIKE '%www.wikidata.org/wiki/%'
  AND idns1.idns_value LIKE 'global'
  AND id.id_namespace_fk = idns1.id
  AND idns2.idns_value LIKE 'wikidata';

# CHECK AFTER
SELECT COUNT (kbc_id) FROM identifier AS id, identifier_namespace AS idns
WHERE id.id_namespace_fk = idns.id
  AND idns.idns_value = 'wikidata';