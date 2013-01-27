package org.gokb

import org.gokb.cred.*;

class TitleLookupService {

    def find(title, issn, eissn) {
      find(title, issn, eissn)
    }

    def find(title, issn, eissn, extra_ids) {

      def result = null

      log.debug("find(${title},${issn},${eissn})");
      def issn_identifier = issn ? Identifier.lookupOrCreateCanonicalIdentifier('issn',issn) : null;
      def eissn_identifier = eissn ? Identifier.lookupOrCreateCanonicalIdentifier('eissn',eissn) : null;

      def tq = TitleInstance.createCriteria()
      def titles = tq.listDistinct {
        ids {
          or {
            'in'('identifier',[issn_identifier,eissn_identifier])
            // eq('identifier',issn_identifier)
            // eq('identifier',eissn_identifier)
          }
        }
      }

      if ( titles ) {
        switch ( titles.size() ) {
          case 0:
            log.error("Should not be here, this case should result in the outer else below");
            break;
          case 1:
            log.debug("Exact match");
            result = titles.get(0);
            break;
          default:
            log.debug("Duplicate matches.. error");
            throw new Exception("Unable to identify unique title based on the supplied identifiers... Not adding");
            break;
        }
      }
      else {
        log.debug("No result, create a new title")
        result = new TitleInstance(name:title);

        if ( ! result.ids )
          result.ids = []

        if ( result.save(flush:true) ) {
          log.debug("New title: ${result.id}");
        }
        else {
          result.errors.each { e ->
            log.error("Problem saving title: ${e}");
          }
        }

        if ( issn_identifier )
          new IdentifierOccurrence(identifier:issn_identifier, component:result).save(flush:true);
        if ( eissn_identifier )
          new IdentifierOccurrence(identifier:eissn_identifier, component:result).save(flush:true);

        extra_ids.each { ei ->
          additional_identifier = Identifier.lookupOrCreateCanonicalIdentifier(ei.type,ei.value)
          new IdentifierOccurrence(identifier:additional_identifier, component:result).save(flush:true);
        }
      }

      // May double check with porter stemmer in the future.. see
      // https://svn.apache.org/repos/asf/lucene/dev/tags/lucene_solr_3_3/lucene/src/java/org/apache/lucene/analysis/PorterStemmer.java

      result;
    }
}
