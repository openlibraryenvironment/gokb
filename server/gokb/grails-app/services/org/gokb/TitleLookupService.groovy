package org.gokb

import org.gokb.cred.*;

class TitleLookupService {

    def find(title, issn, eissn) {
      log.debug("find(${title},${issn},${eissn})");
      def issn_identifier = issn ? Identifier.lookupOrCreateCanonicalIdentifier('issn',issn) : null;
      def eissn_identifier = eissn ? Identifier.lookupOrCreateCanonicalIdentifier('eissn',eissn) : null;

      /*
      def titles = TitleInstance.findAll {
        ids {
          ( ( identifier == issn ) || ( identifier == eissn ) ) 
        }
      }

      if ( titles ) {
        switch ( titles.size() ) {
          case 0:
            log.debug("New title.. create");
            break;
          case 1:
            log.debug("Exact match");
            break;
          default:
            log.debug("Duplicate matches.. error");
            throw new Exception("Unable to identify unique title based on the supplied identifiers... Not adding");
            break;
        }
      }
      else {
        log.error("Title lookupquery returned null. error!");
      }
      */
      // May double check with porter stemmer in the future.. see
      // https://svn.apache.org/repos/asf/lucene/dev/tags/lucene_solr_3_3/lucene/src/java/org/apache/lucene/analysis/PorterStemmer.java
    }
}
