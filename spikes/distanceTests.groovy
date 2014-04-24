#!/usr/bin/groovy

// Groovy Scriptlet experimenting with distance measures

def testcases = [
  [ original : 'The quick brown fox jumps over the lazy dog',
    variants : [ [ txt:'The quick brown fox jumps over the lazy dog', reason:'Exact Match'],
                 [ txt:'Quick brown fox jumps over the lazy dog', reason:'Remove leading article, capitalisation'],
                 [ txt:'WILEY The quick brown fox jumps over the lazy dog', reason:'Spurious prefix'],
                 [ txt:'qk. brn. fox jumps over the lazy dog', reason:'Abbreviations'],
                 [ txt:'This is not the same title!', reason:'Non-Match'] ],
  ],
  [ original : 'OneWordTitle',
    variants : [ [ txt:'OneWordTitle', reason:'Exact Match'],
                 [ txt:'oneWordTitle', reason:'Single Character Case Change'],
                 [ txt:'One Word Title', reason:'Add spaces' ],
                 [ txt:'WILEY OneWordTitle', reason:'Spurious prefix'],
                 [ txt:'OneWordTitle With Some Text', reason:'Spurious Trailing Text' ] ]
  ],
  [ original : null,
    variants : [ [ txt:'ACM Proceedings / Association for Computing Machinery', reason: 'Should not match' ] ]
  ]
]

static List<String> mostSimilar(String pattern, candidates, double threshold = 0) {
  SortedMap<Double, String> sorted = new TreeMap<Double, String>()
  for (candidate in candidates) {
    double score = stringSimilarity(pattern, candidate)
    if (score > threshold) {
      sorted[score] = candidate
    }
  }

  (sorted.values() as List).reverse()
}

private static double stringSimilarity(String s1, String s2, int degree = 2) {
  if ( ( s1 != null ) && ( s2 != null ) ) {
    return similarity(s1.toLowerCase().toCharArray(), s2.toLowerCase().toCharArray(), degree)
  }
  return 0
}

private static double similarity(sequence1, sequence2, int degree = 2) {
  Map<List, Integer> m1 = countNgramFrequency(sequence1, degree)
  Map<List, Integer> m2 = countNgramFrequency(sequence2, degree)

  dotProduct(m1, m2) / Math.sqrt(dotProduct(m1, m1) * dotProduct(m2, m2))
}

private static Map<List, Integer> countNgramFrequency(sequence, int degree) {
  Map<List, Integer> m = [:]
  int count = sequence.size()

  for (int i = 0; i + degree <= count; i++) {
    List gram = sequence[i..<(i + degree)]
    m[gram] = 1 + m.get(gram, 0)
  }

  m
}

private static double dotProduct(Map<List, Integer> m1, Map<List, Integer> m2) {
  m1.keySet().collect { key -> m1[key] * m2.get(key, 0) }.sum()
}

def distance(str1, str2) {
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
    else {
      return 0
    }
}

testcases.each { tc ->
  println("Tests for ${tc.original}:");
  tc.variants.each { v ->
    println("  \"${v.txt}\" (${v.reason}) -> lev:${distance(tc.original,v.txt)} cos:${stringSimilarity(tc.original,v.txt)}");
  }
}
