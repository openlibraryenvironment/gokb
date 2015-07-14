package com.k_int;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtils {
  
	// For string matching.
  // Group 1 = Version number
  // Group 4 = Start letter of versioning word.
  // Group 5 = end versioning number
	public static final String VERSION_REGEX = "((\\d+\\.?)+)(\\-([a-z])[a-z]*(\\d*))?";

  /**
   * Compares two version strings. 
   * 
   * Use this instead of String.compareTo() for a non-lexicographical 
   * comparison that works for version strings. e.g. "1.10".compareTo("1.6").
   * This method also works with versions like... 1.10-alpha2. Only the first character of the word will be compared
   * so version alpha2 and alien2 would be treated equal.
   * 
   * @param str1 a string of ordinal numbers separated by decimal points. 
   * @param str2 a string of ordinal numbers separated by decimal points.
   * @param ignore_trailing_zero a boolean flag to determine whether trailing zeros should be ignored.
   * @return The result is a negative integer if str1 is _numerically_ less than str2. 
   *         The result is a positive integer if str1 is _numerically_ greater than str2. 
   *         The result is zero if the strings are _numerically_ equal.
   */
  public static Integer versionCompare(String str1, String str2, boolean ignore_trailing_zero) {
    
    // Lower case both strings.
    str1 = str1.toLowerCase();
    str2 = str2.toLowerCase();
    
    // We may have a version with a hyphenated word too. (i.e. 1.x-alpha/beta)
    // We should break the word into sections and test each region.
    Pattern p = Pattern.compile("^" + VERSION_REGEX + "$");
    
    // Create the matchers.
    Matcher m1 = p.matcher(str1);
    Matcher m2 = p.matcher(str2);
    
    if (!(m1.matches() && m2.matches())) {
      throw new IllegalArgumentException("Version strings must be in the format 0.0.0-a0.");
    }
    
    // Lets check the first numeric part of the version...
    int result = versionNumberCompare (m1.group(1), m2.group(1), true);
    
    // Work out the group counts.
    if ( result == 0 ) {
      
      // If a version declares a secondary version then it is higher if the primary strings are equal.
      result = Integer.signum(m1.groupCount() - m2.groupCount());
    }
    
    // Both strings are primarily equal and both declare secondary versions. 
    if (result == 0) {
      // Concatinate groups 4 and 5 and compare them aplhanumerically.
      result = Integer.signum((m1.group(4) + m1.group(5)).compareTo(m2.group(4) + m2.group(5)));
    }
    
    // Return the result.
    return result;
  }
  
  public static Integer versionNumberCompare(String str1, String str2, boolean ignore_trailing_zero) {
    
    // If we require trailing zeros to be ignored i.e. Should 1.2 == 1.2.0 == 1.2.0.0?
    if (ignore_trailing_zero) {
      
      // We should just remove any trailing zeros.
      str1 = str1.replaceAll("(\\.0+)*$", "");
      str2 = str2.replaceAll("(\\.0+)*$", "");
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
