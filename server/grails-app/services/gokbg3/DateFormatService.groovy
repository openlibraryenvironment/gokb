package gokbg3

import org.apache.commons.lang3.time.FastDateFormat

import java.text.DateFormat
import java.text.Format
import java.text.SimpleDateFormat

/**
 * Collecting the Dateformatters which are scattered all over the application into one central entity that might be
 * updated to java.time later on.
 */

class DateFormatService {
  private static Format DATE_FORMAT = new FastDateFormat("yyyy-MM-dd", TimeZone.getDefault(), Locale.getDefault())
  private static Format TIMESTAMP_FORMAT = new FastDateFormat("yyyy-MM-dd HH:mm:ss", TimeZone.getDefault(), Locale.getDefault())
  private static Format TIMESTAMP_FORMAT_MS = new FastDateFormat("yyyy-MM-dd HH:mm:ss.SSS", TimeZone.getDefault(), Locale.getDefault())
  private static Format ISO_FORMAT = new FastDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('GMT'), Locale.getDefault())
  private static Format ISO_MS_FORMAT = new FastDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone('GMT'), Locale.getDefault())

  static String formatDate(Date date) {
    DATE_FORMAT.format(date)
  }

  static Date parseDate(String date) {
    DATE_FORMAT.parse(date)
  }

  static String formatTimestamp(Date date) {
    TIMESTAMP_FORMAT.format(date)
  }

  static Date parseTimestamp(String date) {
    TIMESTAMP_FORMAT.parse(date)
  }

  static String formatTimestampMs(Date date) {
    TIMESTAMP_FORMAT_MS.format(date)
  }

  static Date parseTimestampMs(String date) {
    TIMESTAMP_FORMAT_MS.parse(date)
  }

  static String formatIsoTimestamp(Date date) {
    ISO_FORMAT.format(date)
  }

  static Date parseIsoTimestamp(String date) {
    ISO_FORMAT.parse(date)
  }

  static String formatIsoMsTimestamp(Date date) {
    ISO_MS_FORMAT.format(date)
  }

  static Date parseIsoMsTimestamp(String date) {
    ISO_MS_FORMAT.parse(date)
  }
}
