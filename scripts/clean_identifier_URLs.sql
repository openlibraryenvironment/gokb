# http://d-nb.info/gnd/gnd/
# to
# http://d-nb.info/gnd/
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE '%gnd/gnd%';
SELECT * FROM identifier WHERE id_value LIKE '%gnd/%';
UPDATE identifier
set id_value=REGEXP_REPLACE(id_value, '(.*)gnd/gnd(.*)','\1gnd\2')
WHERE id_value LIKE '%gnd/gnd%';
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
UPDATE identifier
set id_value=REGEXP_REPLACE(id_value, '(.*)d-nb.info/(.*)','\1d-nb.info/gnd/\2')
WHERE id_value SIMILAR TO '%d-nb.info/[0-9]%';
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
UPDATE identifier
set id_value=REGEXP_REPLACE(id_value, 'http://d-nb.info/(.*)','https://d-nb.info/\1')
WHERE id_value LIKE 'http://d-nb.info/%';
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
UPDATE identifier
set id_value=REGEXP_REPLACE(id_value, 'http://dbpedia.org/page/(.*)','http://dbpedia.org/resource/\1')
WHERE id_value LIKE 'http://dbpedia.org/page/%';
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
UPDATE identifier
set id_value=REGEXP_REPLACE(id_value, 'http://id.loc.gov/authorities/names/(.*).html','http://id.loc.gov/authorities/names/\1')
WHERE id_value LIKE 'http://id.loc.gov/authorities/names/%.html';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE 'http://id.loc.gov/authorities/names/%';
SELECT * FROM identifier WHERE id_value LIKE 'http://id.loc.gov/authorities/names/%.html';


# http://id.loc.gov/authorities/names/<id>.html
# entfernen
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE '%freebase%';
DELETE FROM identifier WHERE id_value LIKE '%freebase%';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE '%freebase%';


# http://www.lib.ncsu.edu/ld/onld/
# to
# https://www.lib.ncsu.edu/ld/onld/
#
# CHECK BEFORE
SELECT * FROM identifier WHERE id_value LIKE 'http://www.lib.ncsu.edu/ld/onld/%';
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%';
UPDATE identifier
set id_value=REGEXP_REPLACE(id_value, 'http://www.lib.ncsu.edu/ld/onld/(.*)','https://www.lib.ncsu.edu/ld/onld/\1')
WHERE id_value LIKE 'http://www.lib.ncsu.edu/ld/onld/%';
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
UPDATE identifier
set id_value=REGEXP_REPLACE(id_value, 'https://www.lib.ncsu.edu/ld/onld/(.*).html','https://www.lib.ncsu.edu/ld/onld/\1')
WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%.html';
# CHECK AFTER
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%';
SELECT * FROM identifier WHERE id_value LIKE 'https://www.lib.ncsu.edu/ld/onld/%.html';
