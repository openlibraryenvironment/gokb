# http://d-nb.info/gnd/gnd/
# to
# http://d-nb.info/gnd/
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE '%gnd/gnd%';
SELECT * FROM identifier WHERE id_value LIKE '%gnd/%';
UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)gnd/gnd(.*)','\1gnd\2')
FROM identifier_namespace AS idns
WHERE id.id_value LIKE '%gnd/gnd%'
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE '%gnd/gnd%';
SELECT * FROM identifier WHERE id_value LIKE '%gnd/%';


# http://d-nb.info/
# to
# http://d-nb.info/gnd/
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value SIMILAR TO '%d-nb.info/[0-9]%';
SELECT * FROM identifier WHERE id_value LIKE '%d-nb.info/gnd%';
UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, '(.*)d-nb.info/(.*)','\1d-nb.info/gnd/\2')
FROM identifier_namespace AS idns
WHERE id.id_value SIMILAR TO '%d-nb.info/[0-9]%'
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value SIMILAR TO '%d-nb.info/[0-9]%';
SELECT * FROM identifier WHERE id_value LIKE '%d-nb.info/gnd%';


# http://d-nb.info/
# to
# https://d-nb.info/
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE 'http://d-nb.info/%';
SELECT * FROM identifier WHERE id_value LIKE 'https://d-nb.info/%';
UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, 'http://d-nb.info/(.*)','https://d-nb.info/\1')
FROM identifier_namespace AS idns
WHERE id.id_value LIKE 'http://d-nb.info/%'
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE 'http://d-nb.info/%';
SELECT * FROM identifier WHERE id_value LIKE 'https://d-nb.info/%';


# http://dbpedia.org/page/
# to
# http://dbpedia.org/resource/
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE 'http://dbpedia.org/page/%';
SELECT * FROM identifier WHERE id_value LIKE 'http://dbpedia.org/resource/%';
UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, 'http://dbpedia.org/page/(.*)','http://dbpedia.org/resource/\1')
FROM identifier_namespace AS idns
WHERE id.id_value LIKE 'http://dbpedia.org/page/%'
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE 'http://dbpedia.org/page/%';
SELECT * FROM identifier WHERE id_value LIKE 'http://dbpedia.org/resource/%';


# http://id.loc.gov/authorities/names/<id>.html
# to
# http://id.loc.gov/authorities/names/<id>
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE 'http://id.loc.gov/authorities/names/%';
SELECT * FROM identifier WHERE id_value LIKE 'http://id.loc.gov/authorities/names/%.html';
UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, 'http://id.loc.gov/authorities/names/(.*).html','http://id.loc.gov/authorities/names/\1')
FROM identifier_namespace AS idns
WHERE id.id_value LIKE 'http://id.loc.gov/authorities/names/%.html'
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE 'http://id.loc.gov/authorities/names/%';
SELECT * FROM identifier WHERE id_value LIKE 'http://id.loc.gov/authorities/names/%.html';


# delete '%freebase%' Identifiers
# set status to "Rejected"
# assuming EditStatus "In Progress == 19"
# and EditStatus "Rejected == 20"
# CHECK BEFORE
SELECT id.kbc_id FROM identifier AS id, kbcomponent AS kbc WHERE id.id_value LIKE '%freebase%' AND id.kbc_id = kbc.kbc_id;
SELECT id.kbc_id FROM identifier AS id, kbcomponent AS kbc WHERE id.kbc_id = kbc.kbc_id AND kbc.edit_status_id = 19; # CHECK value
SELECT id.kbc_id FROM identifier AS id, kbcomponent AS kbc WHERE id.kbc_id = kbc.kbc_id AND kbc.edit_status_id = 20; # CHECK value
# UPDATE;
UPDATE kbcomponent AS kbc
SET edit_status_id = 20         # CHECK value
FROM identifier AS id, identifier_namespace AS idns
WHERE kbc.kbc_id = id.kbc_id
  AND id.id_value LIKE '%freebase%'
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global';
# CHECK AFTER
SELECT id.kbc_id FROM identifier AS id, kbcomponent AS kbc WHERE id.kbc_id = kbc.kbc_id AND kbc.edit_status_id = 19; # CHECK value
SELECT id.kbc_id FROM identifier AS id, kbcomponent AS kbc WHERE id.kbc_id = kbc.kbc_id AND kbc.edit_status_id = 20; # CHECK value
SELECT id.kbc_id FROM identifier AS id, kbcomponent AS kbc WHERE id.id_value LIKE '%freebase%' AND id.kbc_id = kbc.kbc_id;
#
# and run admin function "Expunge Rejected Components"



# http://www.lib.ncsu.edu/ld/onld/
# to
# https://www.lib.ncsu.edu/ld/onld/
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE 'http://www.lib.ncsu.edu/ld/onld/%';
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%';
UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, 'http://www.lib.ncsu.edu/ld/onld/(.*)','https://www.lib.ncsu.edu/ld/onld/\1')
FROM identifier_namespace AS idns
WHERE id.id_value LIKE 'http://www.lib.ncsu.edu/ld/onld/%'
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE 'http://www.lib.ncsu.edu/ld/onld/%';
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%';


# https://www.lib.ncsu.edu/ld/onld/<id>.html
# to
# https://www.lib.ncsu.edu/ld/onld/<id>
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%';
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%.html';
UPDATE identifier AS id
SET id_value=REGEXP_REPLACE(id.id_value, 'https://www.lib.ncsu.edu/ld/onld/(.*).html','https://www.lib.ncsu.edu/ld/onld/\1')
FROM identifier_namespace AS idns
WHERE id.id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%.html'
  AND id.id_namespace_fk = idns.id
  AND idns.idns_value = 'global';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%';
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%.html';
