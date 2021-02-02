package org.gokb

import java.text.Normalizer
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.LocalDateTime
import java.time.LocalDate


class GOKbTextUtils {

  private static final List<String> STOPWORDS = [
    "and",
    "the",
    "from"
  ];
  public static final DateTimeFormatter dateformatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
  public static final DateTimeFormatter datetimeformatter = DateTimeFormatter.ofPattern("" + "[uuuu-MM-dd' 'HH:mm:ss.SSS]" + "[uuuu-MM-dd' 'HH:mm:ss.S]" + "[uuuu-MM-dd'T'HH:mm:ss'Z']")

  public static int levenshteinDistance(String str1, String str2) {
    if ( ( str1 != null ) && ( str2 != null ) ) {
      def str1_len = str1.length()
      def str2_len = str2.length()
      int[][] distance = new int[str1_len + 1][str2_len + 1]
      (str1_len + 1).times { distance[it][0] = it }
      (str2_len + 1).times { distance[0][it] = it }
      (1..str1_len).each { i ->
        (1..str2_len).each { j ->
          distance[i][j] = [distance[i-1][j]+1, distance[i][j-1]+1, str1[i-1]==str2[j-1]?distance[i-1][j-1]:distance[i-1][j-1]+1].min()
        }
      }
      return distance[str1_len][str2_len]
    }

    return 0
  }

  public static String generateComparableKey(String s) {
    // Ensure s is not null.
    if (!s) s = "";

    // Normalize to the D Form and then remove diacritical marks.
    s = Normalizer.normalize(s, Normalizer.Form.NFD)
    s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+","");

    // lowercase.
    s = s.toLowerCase();

    // Break apart the string.
    String[] components = s.split("\\s");

    // Sort the parts.
    Arrays.sort(components);

    // Re-piece the array back into a string.
    String normstring = "";
    components.each { String piece ->
      if ( !STOPWORDS.contains(piece)) {

        // Remove all unnecessary characters.
        normstring += piece.replaceAll("[^a-z0-9]", " ") + " ";
      }
    }

    normstring = normstring.trim().replaceAll(" +", " ")

    // If something has gone cataclysmically wrong (Like, for example, a title consisting of only wide utf-8 characters, or a title like "/." which appears
    // in the askews file, then we fall back on the string we were originally given.
    if ( normstring == '' )
      normstring = s

    normstring
  }

  public static String normaliseString(String s) {

    // Ensure s is not null.
    if (!s) s = "";

    // Normalize to the D Form and then remove diacritical marks.
    s = Normalizer.normalize(s, Normalizer.Form.NFD)
    s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+","");

    // lowercase.
    s = s.toLowerCase();

    // Break apart the string.
    String[] components = s.split("\\s");

    // Re-piece the array back into a string.
    String normstring = "";
    components.each { String piece ->
      if ( !STOPWORDS.contains(piece)) {

        // Remove all unnecessary characters.
        normstring += piece.replaceAll("[^a-z0-9]", " ") + " ";
      }
    }

    normstring = normstring.trim().replaceAll(" +", " ")

    // If something has gone cataclysmically wrong (Like, for example, a title consisting of only wide utf-8 characters, or a title like "/." which appears
    // in the askews file, then we fall back on the string we were originally given.
    if ( !normstring?.trim() )
      normstring = s

    normstring
  }

  public static double cosineSimilarity(String s1, String s2, int degree = 2) {
    if ( ( s1 != null ) && ( s2 != null ) ) {
      return cosineSimilarity(s1.toLowerCase()?.toCharArray(), s2.toLowerCase()?.toCharArray(), degree)
    }

    return 0
  }

  /**
   * Concatenate the components (Which should already be normalised strings) and then hash them
   */
  public static String generateComponentHash(List components) {

    def sw = new StringWriter()
    def first = true;
    components.each { c ->
      if ( c ) {
        if ( first ) { first = false; } else { sw.write (' '); }
        sw.write(c)
      }
    }

    return MessageDigest.getInstance("MD5").digest(sw.toString().bytes).encodeHex().toString()
  }

  public static String norm1(List components) {
    def sw = new StringWriter()
    def first = true;
    components.each { c ->
      if ( c ) {
        if ( first ) { first = false; } else { sw.write (' '); }
        sw.write(c)
      }
    }

    return norm2(sw.toString()).trim().toLowerCase()
  }

  public static String norm2(String s) {

    // Ensure s is not null.
    if (!s) s = "";

    // Normalize to the D Form and then remove diacritical marks.
    s = Normalizer.normalize(s, Normalizer.Form.NFD)
    s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+","");

    // lowercase.
    s = s.toLowerCase();

    // Break apart the string.
    String[] components = s.split("\\s");

    Arrays.sort(components);

    // Re-piece the array back into a string.
    String normstring = "";
    components.each { String piece ->
      if ( !STOPWORDS.contains(piece)) {

        // Remove all unnecessary characters.
        normstring += piece.replaceAll("[^a-z0-9]", " ") + " ";
      }
    }

    // normstring.trim().replaceAll(" +", " ")
    // Do spaces really add anything for our purposes here, or are random spaces more likely to creep in to the
    // source records and throw the matching? Suspect the latter, kill them for now
    normstring.trim().replaceAll(' ', '')
  }

  private static Map<List, Integer> countNgramFrequency(char[] sequence, int degree) {
    Map<List, Integer> m = [:]

    if (sequence) {
      int count = sequence.size()

      for (int i = 0; i + degree <= count; i++) {
        List gram = sequence[i..<(i + degree)]
        m[gram] = 1 + m.get(gram, 0)
      }
    }

    m
  }

  private static double dotProduct(Map<List, Integer> m1, Map<List, Integer> m2) {
    m1.keySet().collect { key -> m1[key] * m2.get(key, 0) }.sum()
  }

  public static double cosineSimilarity(char[] sequence1, char[] sequence2, int degree = 2) {
    Map<List, Integer> m1 = countNgramFrequency(sequence1, degree)
    Map<List, Integer> m2 = countNgramFrequency(sequence2, degree)

    dotProduct(m1, m2) / Math.sqrt(dotProduct(m1, m1) * dotProduct(m2, m2))
  }

  public static LocalDateTime completeDateString(String datepart, boolean start = true) {
    def result = null

    if ( datepart?.trim() ) {
      try {
        if ( datepart.length() == 4 ) {
          if (start) {
            result = LocalDate.parse(datepart + "-01-01", dateformatter).atStartOfDay()
          }
          else {
            result = LocalDate.parse(datepart + "-12-31", dateformatter).atStartOfDay()
          }
        }
        else if ( datepart.length() == 7 ) {
          if(start) {
            result = LocalDate.parse(datepart + "-01", dateformatter).atStartOfDay()
          }
          else {
            result = LocalDate.parse(datepart + "-31", dateformatter).atStartOfDay()
          }
        }
        else if ( datepart.length() == 10 ) {
          result = LocalDate.parse(datepart, dateformatter).atStartOfDay()
        }
        else {
          result = LocalDateTime.parse(datepart, datetimeformatter)
        }
      }
      catch (Exception pe) {
      }
    }
    result
  }
}
