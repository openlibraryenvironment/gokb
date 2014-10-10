package com.k_int;

public class TextUtils {

  /**
   * Compares two version strings. 
   * 
   * Use this instead of String.compareTo() for a non-lexicographical 
   * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
   * 
   * @param str1 a string of ordinal numbers separated by decimal points. 
   * @param str2 a string of ordinal numbers separated by decimal points.
   * @param ignore_trailing_zero a boolean flag to determine whether trailing zeros should be ignored.
   * @return The result is a negative integer if str1 is _numerically_ less than str2. 
   *         The result is a positive integer if str1 is _numerically_ greater than str2. 
   *         The result is zero if the strings are _numerically_ equal.
   */
  public static Integer versionCompare(String str1, String str2, boolean ignore_trailing_zero) {
    
    // If we require trailing zeros to be ignored i.e. Should 1.2 == 1.2.0 == 1.2.0.0?
    if (ignore_trailing_zero) {
      
      // We should just remove any trailing zeros.
      str1 = str1.replaceAll("(\\.0)*$", "");
      str2 = str2.replaceAll("(\\.0)*$", "");
    }
    
    String[] vals1 = str1.split("\\.");
    String[] vals2 = str2.split("\\.");
    int i = 0;
    
    // Set index to first non-equal ordinal or length of shortest version string.
    while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
      i++;
    }
    
    // Compare first non-equal ordinal number.
    if (i < vals1.length && i < vals2.length) {
      int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
      return Integer.signum(diff);
    }
    
    // The strings are equal or one string is a substring of the other
    // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
    // Therefore we can assume the longer string is the highest version.
    return Integer.signum(vals1.length - vals2.length);
  }
  
  /**
   *{@code ignore_trailing_zero} defaults to <code>true</code>.
   * @see TextUtils#versionCompare(String, String, boolean)
   */
  public static Integer versionCompare(String str1, String str2) {
    
    // Default to ignoring trailing .0
    return versionCompare(str1, str2, true);
  }
}
