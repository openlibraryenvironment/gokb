package gokbg3

import grails.gorm.transactions.Transactional

import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 * Collecting the Dateformatters which are scattered all over the application into one central entity that might be
 * updated to java.time later on.
 */
@Transactional
class DateFormatService {
  private static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd")
  private static DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  private static DateFormat TIMESTAMP_FORMAT_MS = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS")
  private static DateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

  String formatDate(Date date) {
    DATE_FORMAT.format(date)
  }

  Date parseDate(String date) {
    DATE_FORMAT.parse(date)
  }

  String formatTimestamp(Date date) {
    TIMESTAMP_FORMAT.format(date)
  }

  Date parseTimestamp(String date) {
    TIMESTAMP_FORMAT.parse(date)
  }

  String formatTimestampMs(Date date) {
    TIMESTAMP_FORMAT_MS.format(date)
  }

  Date parseTimestampMs(String date) {
    TIMESTAMP_FORMAT_MS.parse(date)
  }

  String formatIsoTimestamp(Date date) {
    ISO_FORMAT.format(date)
  }

  Date parseIsoTimestamp(String date) {
    ISO_FORMAT.parse(date)
  }
}
