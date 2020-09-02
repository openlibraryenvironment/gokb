Run isql

sparql load <file:///tmp/ONA.rdf> into <test>;


Queries

everything

https://gokb.k-int.com/sparql?default-graph-uri=&query=select+%3Fs+%3Fp+%3Fo+where+%7B%3Fs+%3Fp+%3Fo%7D&format=text%2Fhtml&timeout=0&debug=on


select ?s where {
?s <http://www.loc.gov/standards/mods/modsrdf/v1/role> "publisher"
}

https://gokb.k-int.com/sparql?default-graph-uri=&query=select+%3Fs+where+%7B%0D%0A%3Fs+%3Chttp%3A%2F%2Fwww.loc.gov%2Fstandards%2Fmods%2Fmodsrdf%2Fv1%2Frole%3E+%22publisher%22%0D%0A%7D&format=text%2Fhtml&timeout=0&debug=on

select ?s ?name where {
?s <http://www.loc.gov/standards/mods/modsrdf/v1/role> "publisher".
?s <http://www.w3.org/2004/02/skos/core#prefLabel> ?name
}

https://gokb.k-int.com/sparql?default-graph-uri=&query=select+%3Fs+%3Fname+where+%7B%0D%0A%3Fs+%3Chttp%3A%2F%2Fwww.loc.gov%2Fstandards%2Fmods%2Fmodsrdf%2Fv1%2Frole%3E+%22publisher%22.%0D%0A%3Fs+%3Chttp%3A%2F%2Fwww.w3.org%2F2004%2F02%2Fskos%2Fcore%23prefLabel%3E+%3Fname%0D%0A%7D&format=text%2Fhtml&timeout=0&debug=on



select ?p ?o {
<http://lib.ncsu.edu/ona/ona00000459> ?p ?o .
}

https://gokb.k-int.com/sparql?default-graph-uri=&query=select+%3Fp+%3Fo+%7B%0D%0A%3Chttp%3A%2F%2Flib.ncsu.edu%2Fona%2Fona00000459%3E+%3Fp+%3Fo+.%0D%0A%7D&format=text%2Fhtml&timeout=0&debug=on
