package org.gokb

import java.text.Normalizer

class GoKbTextUtils {
  
  private static final List<String> STOPWORDS = [
	"and",
	"the",
	"from"
  ];

  def static int levenshteinDistance(String str1, String str2) {
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
    distance[str1_len][str2_len]
  }
  
  def static String normaliseString(String s) {

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
	components.each { String piece
	  if ( !STOPWORDS.contains(piece)) {

		// Remove all unnecessary characters.
		normstring += piece.replaceAll("[^a-z0-9]", " ") + " ";
	  }
	}

	normstring.trim();
  }
}
