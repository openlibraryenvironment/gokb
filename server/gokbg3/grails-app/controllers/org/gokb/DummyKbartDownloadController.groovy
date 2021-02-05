package org.gokb

import com.google.common.base.Utf8
import grails.plugin.springsecurity.annotation.Secured
import grails.util.Environment
import org.apache.commons.codec.binary.Hex

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ThreadLocalRandom

/**
 * Generates dummy kbart data for testing
 */
class DummyKbartDownloadController {

  private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("YYYY-MM-dd")
  private static DateTimeFormatter plain = DateTimeFormatter.ofPattern("dd.MM.YYYY")
  private static int BOOK = 0
  private static int JOURNAL = 1
  private myService = new DummyKbartService()

  @Secured(value = ['IS_AUTHENTICATED_ANONYMOUSLY'])
  def index() {
    if (Environment.current in [Environment.TEST, Environment.DEVELOPMENT]) {
      def max = 100
      if (params.num) {
        max = Integer.parseInt(params.num)
      }

      StringBuilder data = new StringBuilder()
      data.append(myService.tabHead())
      for (int i = 0; i < max; i++) {
        data.append(myService.row())
      }
      render data.toString()
    }
    else {
      response.status=404
    }
  }

  private class DummyKbartService {
    private String tabHead() {
      StringBuilder line = new StringBuilder()
      boolean first = true
      def title = generateSerial()
      title.each { column, value ->
        if (!first) {
          line.append("\t")
        } else {
          first = false
        }
        line.append(column)
      }
      line.append("\n")
      return line.toString()
    }

    private String row() {
      StringBuilder line = new StringBuilder()
      def lineData = ThreadLocalRandom.current().nextBoolean()
          ? ThreadLocalRandom.current().nextBoolean() ? generateBook() : generateSerial()
          : ThreadLocalRandom.current().nextBoolean() ? generateDatabase() : generateOther()
      boolean first = true
      lineData.each { column, value ->
        if (!first) {
          line.append("\t")
        } else {
          first = false
        }
        line.append(value)
      }
      line.append("\n")
      return line.toString()
    }

    private def generateBook() {
      def record = [:]
      def randomCode = Long.toHexString(ThreadLocalRandom.current().nextLong())
      LocalDate[] timeline = dateSequence(5)
      record.publication_title = "dummy book title $randomCode"
      record.print_identifier = isbn()
      record.online_identifier = isbn()
      record.date_first_issue_online = ""// timeline[2].format(plain)
      record.num_first_vol_online = ""//ThreadLocalRandom.current().nextInt(100)
      record.num_first_issue_online = ""//ThreadLocalRandom.current().nextInt(100)
      record.date_last_issue_online = ""//timeline[3].format(plain)
      record.num_last_vol_online = ""//ThreadLocalRandom.current().nextInt(100, 200)
      record.num_last_issue_online = ""//ThreadLocalRandom.current().nextInt(100, 200)
      record.title_url = "http://some.domain.de/$randomCode"
      record.first_author = "author $randomCode"
      record.title_id = randomCode
      record.embargo_info = ""
      record.coverage_Depth = depth()
      record.notes = "notes $randomCode"
      record.publication_type = "monograph"
      record.publisher_name = "publisher name $randomCode"
      record.date_monograph_published_print = timeline[0].format(plain)
      record.date_monograph_published_online = timeline[1].format(plain)
      record.monograph_volume = ThreadLocalRandom.current().nextInt(89)
      record.monograph_edition = randomCode
      record.first_editor = "first editor $randomCode"
      record.parent_publication_title_id = ""
      record.preceding_publication_title_id = ""
      record.access_type = accessType()
      record.zdb_id = ""// record.zdb_id = "${ThreadLocalRandom.current().nextLong(1000000000)}-${["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "x", "X"][ThreadLocalRandom.current().nextInt(12)]}"
      record.last_changed = timeline[4].format(dtf)
      record.access_start_date = (timeline[2] <= timeline[3] ? timeline[2] : timeline[3]).format(dtf)
      record.access_end_date = (timeline[2] > timeline[3] ? timeline[2] : timeline[3]).format(dtf)
      record.medium = ""
      record.listprice_eur = price(100)
      record.listprice_gbp = price(80)
      record.listprice_usd = price(120)

      return record
    }

    private def generateSerial() {
      def record = [:]
      def randomCode = Long.toHexString(ThreadLocalRandom.current().nextLong())
      LocalDate[] timeline = dateSequence(5)
      record.publication_title = "dummy serial title $randomCode"
      record.print_identifier = issn()
      record.online_identifier = issn()
      record.date_first_issue_online = timeline[2].isBefore(timeline[3]) ? timeline[2].format(plain) : timeline[3].format(plain)
      record.num_first_vol_online = ThreadLocalRandom.current().nextInt(100)
      record.num_first_issue_online = ThreadLocalRandom.current().nextInt(100)
      record.date_last_issue_online = timeline[2].isBefore(timeline[3]) ? timeline[3].format(plain) : timeline[2].format(plain)
      record.num_last_vol_online = ThreadLocalRandom.current().nextInt(100, 200)
      record.num_last_issue_online = ThreadLocalRandom.current().nextInt(100, 200)
      record.title_url = "http://some.host.de/$randomCode"
      record.first_author = "author $randomCode"
      record.title_id = randomCode
      record.embargo_info = ""
      record.coverage_Depth = depth()
      record.notes = "notes $randomCode"
      record.publication_type = "serial"
      record.publisher_name = "publisher name $randomCode"
      record.date_monograph_published_print = ""//timeline[0].format(plain)
      record.date_monograph_published_online = ""//timeline[1].format(plain)
      record.monograph_volume = ""
      record.monograph_edition = ""
      record.first_editor = "first editor $randomCode"
      record.parent_publication_title_id = ""
      record.preceding_publication_title_id = ""
      record.access_type = accessType()
      record.zdb_id = record.zdb_id = "${ThreadLocalRandom.current().nextLong(1000000000)}-${["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "x", "X"][ThreadLocalRandom.current().nextInt(12)]}"
      record.last_changed = timeline[4].format(dtf)
      record.access_start_date = (timeline[2] <= timeline[3] ? timeline[2] : timeline[3]).format(dtf)
      record.access_end_date = (timeline[2] > timeline[3] ? timeline[2] : timeline[3]).format(dtf)
      record.medium = ""
      record.listprice_eur = price(50)
      record.listprice_gbp = price(35)
      record.listprice_usd = price(65)

      return record
    }

    private def generateOther() {
      def record = [:]
      def randomCode = Long.toHexString(ThreadLocalRandom.current().nextLong())
      LocalDate[] timeline = dateSequence(5)
      record.publication_title = "dummy other title $randomCode"
      record.print_identifier = issn()
      record.online_identifier = issn()
      record.date_first_issue_online = timeline[2].isBefore(timeline[3]) ? timeline[2].format(plain) : timeline[3].format(plain)
      record.num_first_vol_online = ThreadLocalRandom.current().nextInt(100)
      record.num_first_issue_online = ThreadLocalRandom.current().nextInt(100)
      record.date_last_issue_online = timeline[2].isBefore(timeline[3]) ? timeline[3].format(plain) : timeline[2].format(plain)
      record.num_last_vol_online = ThreadLocalRandom.current().nextInt(100, 200)
      record.num_last_issue_online = ThreadLocalRandom.current().nextInt(100, 200)
      record.title_url = "http://some.host.de/$randomCode"
      record.first_author = "author $randomCode"
      record.title_id = randomCode
      record.embargo_info = ""
      record.coverage_Depth = depth()
      record.notes = "notes $randomCode"
      record.publication_type = "other"
      record.publisher_name = "publisher name $randomCode"
      record.date_monograph_published_print = ""//timeline[0].format(plain)
      record.date_monograph_published_online = ""//timeline[1].format(plain)
      record.monograph_volume = ""
      record.monograph_edition = ""
      record.first_editor = "first editor $randomCode"
      record.parent_publication_title_id = ""
      record.preceding_publication_title_id = ""
      record.access_type = accessType()
      record.zdb_id = record.zdb_id = "${ThreadLocalRandom.current().nextLong(1000000000)}-${["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "x", "X"][ThreadLocalRandom.current().nextInt(12)]}"
      record.last_changed = timeline[4].format(dtf)
      record.access_start_date = (timeline[2] <= timeline[3] ? timeline[2] : timeline[3]).format(dtf)
      record.access_end_date = (timeline[2] > timeline[3] ? timeline[2] : timeline[3]).format(dtf)
      record.medium = ThreadLocalRandom.current().nextInt(3) == 1?medium():""
      record.listprice_eur = price(50)
      record.listprice_gbp = price(35)
      record.listprice_usd = price(65)

      return record
    }

    private def generateDatabase() {
      def record = [:]
      def randomCode = Long.toHexString(ThreadLocalRandom.current().nextLong())
      LocalDate[] timeline = dateSequence(5)
      record.publication_title = "dummy database title $randomCode"
      record.print_identifier = issn()
      record.online_identifier = issn()
      record.date_first_issue_online = timeline[2].isBefore(timeline[3]) ? timeline[2].format(plain) : timeline[3].format(plain)
      record.num_first_vol_online = ThreadLocalRandom.current().nextInt(100)
      record.num_first_issue_online = ThreadLocalRandom.current().nextInt(100)
      record.date_last_issue_online = timeline[2].isBefore(timeline[3]) ? timeline[3].format(plain) : timeline[2].format(plain)
      record.num_last_vol_online = ThreadLocalRandom.current().nextInt(100, 200)
      record.num_last_issue_online = ThreadLocalRandom.current().nextInt(100, 200)
      record.title_url = "http://some.host.de/$randomCode"
      record.first_author = "author $randomCode"
      record.title_id = randomCode
      record.embargo_info = ""
      record.coverage_Depth = depth()
      record.notes = "notes $randomCode"
      record.publication_type = "other"
      record.publisher_name = "publisher name $randomCode"
      record.date_monograph_published_print = ""//timeline[0].format(plain)
      record.date_monograph_published_online = ""//timeline[1].format(plain)
      record.monograph_volume = ""
      record.monograph_edition = ""
      record.first_editor = "first editor $randomCode"
      record.parent_publication_title_id = ""
      record.preceding_publication_title_id = ""
      record.access_type = accessType()
      record.zdb_id = record.zdb_id = "${ThreadLocalRandom.current().nextLong(1000000000)}-${["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "x", "X"][ThreadLocalRandom.current().nextInt(12)]}"
      record.last_changed = timeline[4].format(dtf)
      record.access_start_date = (timeline[2] <= timeline[3] ? timeline[2] : timeline[3]).format(dtf)
      record.access_end_date = (timeline[2] > timeline[3] ? timeline[2] : timeline[3]).format(dtf)
      record.medium = medium()
      record.listprice_eur = price(50)
      record.listprice_gbp = price(35)
      record.listprice_usd = price(65)

      return record
    }

    private LocalDate[] dateSequence(int num) {
      LocalDate[] result = new LocalDate[num]
      long begin = LocalDate.of(1850, 01, 01).toEpochDay()
      long now = LocalDate.now().toEpochDay()
      long[] epochDays = new long[num]

      for (int i = 0; i < num; i++) {
        long neu = ThreadLocalRandom.current().nextLong(begin, now)
        while (neu in epochDays) {
          neu = ThreadLocalRandom.current().nextLong(begin, now)
        }
        epochDays[i] = neu
      }
      Arrays.sort(epochDays)
      for (int i = 0; i < num; i++) {
        result[i] = LocalDate.ofEpochDay(epochDays[i])
      }
      return result
    }

    private String isbn() {
      int pre = [978, 979][ThreadLocalRandom.current().nextInt(2)]
      int grp, pub, tit, chk
      def cmp
      boolean ok = false
      while (!ok) {
        if (pre == 979) {
          grp = [8, 10, 11, 12][ThreadLocalRandom.current().nextInt(4)]
        } else {
          switch (ThreadLocalRandom.current().nextInt(5)) {
            case 0: grp = [0, 1, 2, 3, 4, 5, 7][ThreadLocalRandom.current().nextInt(7)]
              break
            case 1: grp = ThreadLocalRandom.current().nextInt(80, 95)
              break
            case 2: grp = ThreadLocalRandom.current().nextBoolean() ?
              ThreadLocalRandom.current().nextInt(600, 650) :
              ThreadLocalRandom.current().nextInt(950, 990)
              break
            case 3: grp = ThreadLocalRandom.current().nextInt(9900, 9990)
              break
            case 4: grp = ThreadLocalRandom.current().nextInt(99900, 100000)
              break
          }
        }
        pub = ThreadLocalRandom.current().nextInt(1, 10000)
        cmp = "$pre$grp$pub"
        tit = ThreadLocalRandom.current().nextInt(1, 10000)
        ok = 13 > cmp.length() + "$tit".length()
      }
      String pad = "0000000".substring(0, 12 - cmp.length() - "$tit".length())
      cmp += pad
      cmp += "$tit"
      int z1 = Integer.parseInt(cmp[0])
      int z2 = Integer.parseInt(cmp[1])
      int z3 = Integer.parseInt(cmp[2])
      int z4 = Integer.parseInt(cmp[3])
      int z5 = Integer.parseInt(cmp[4])
      int z6 = Integer.parseInt(cmp[5])
      int z7 = Integer.parseInt(cmp[6])
      int z8 = Integer.parseInt(cmp[7])
      int z9 = Integer.parseInt(cmp[8])
      int z10 = Integer.parseInt(cmp[9])
      int z11 = Integer.parseInt(cmp[10])
      int z12 = Integer.parseInt(cmp[11])
      chk = (10 - ((z1 + z3 + z5 + z7 + z9 + z11 + (3 * (z2 + z4 + z6 + z8 + z10 + z12))) % 10)) % 10
//      log.debug("${z1 + z3 + z5 + z7 + z9 + z11} + ${(3 * (z2 + z4 + z6 + z8 + z10 + z12))} = ${(z1 + z3 + z5 + z7 + z9 + z11 + (3 * (z2 + z4 + z6 + z8 + z10 + z12)))}")
      String fin = "$pre-$grp-$pub-$pad$tit-$chk"
      return fin
    }

    private String issn() {
      long num = ThreadLocalRandom.current().nextLong(1,9999999)
      def cmp ="000000$num"
        cmp=cmp.substring(cmp.length()-8, cmp.length()-1)
      int z1 = Integer.parseInt(cmp[0])
      int z2 = Integer.parseInt(cmp[1])
      int z3 = Integer.parseInt(cmp[2])
      int z4 = Integer.parseInt(cmp[3])
      int z5 = Integer.parseInt(cmp[4])
      int z6 = Integer.parseInt(cmp[5])
      int z7 = Integer.parseInt(cmp[6])
      int chk = (8*z1 + 7*z2 + 6*z3 + 5*z4 + 4*z5 + 3*z6 + 2*z7)%11
      String fin = "$z1$z2$z3$z4-$z5$z6$z7${chk<10?chk:'X'}"
      return fin
    }

    private String depth() {
      return ["fulltext", "selected articles", "abstracts"][ThreadLocalRandom.current().nextInt(3)]
    }

    private String accessType() {
      return ["P", "F"][ThreadLocalRandom.current().nextInt(2)]
    }

    private String medium() {
      return ["A & I Database", "Audio", "Book", "Database", "Dataset", "Film", "Image", "Journal",
              "Other", "Published Score", "Article", "Software", "Statistics", "Market Data", "Standards",
              "Biography", "Legal Text", "Cartography", "Miscellaneous"][ThreadLocalRandom.current().nextInt(19)]
    }

    private String price(int max = 100) {
      return "${ThreadLocalRandom.current().nextInt(max)}.${ThreadLocalRandom.current().nextInt(100)}"
    }

    private LocalDate between(LocalDate startInclusive, LocalDate endExclusive) {
      long startEpochDay = startInclusive.toEpochDay()
      long endEpochDay = endExclusive.toEpochDay()
      long randomDay = ThreadLocalRandom.current().nextLong(startEpochDay, endEpochDay)

      return LocalDate.ofEpochDay(randomDay)
    }
  }

}
