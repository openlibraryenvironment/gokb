package org.gokb


class GoKbTextUtils {

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
}
