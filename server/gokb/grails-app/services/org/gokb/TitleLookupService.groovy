package org.gokb

import org.gokb.cred.*;

class TitleLookupService {

    def find(title, issn, eissn) {
      log.debug("find(${title},${issn},${eissn})");
      def issn_identifier = issn ? Identifier.lookupOrCreateCanonicalIdentifier('issn',issn) : null;
      def eissn_identifier = eissn ? Identifier.lookupOrCreateCanonicalIdentifier('eissn',eissn) : null;

      // May double check with porter stemmer in the future.. see
      // https://svn.apache.org/repos/asf/lucene/dev/tags/lucene_solr_3_3/lucene/src/java/org/apache/lucene/analysis/PorterStemmer.java
    }
}
