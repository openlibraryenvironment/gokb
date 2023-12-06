package org.gokb.dto


class KBartRecord {

  String publication_title
  String print_identifier
  String online_identifier
  String date_first_issue_online
  String num_first_vol_online
  String num_first_issue_online
  String date_last_issue_online
  String num_last_vol_online
  String num_last_issue_online
  String title_url
  String first_author
  String title_id
  String embargo_info
  String coverage_depth
  String coverage_notes
  String publisher_name
  String publication_type  //"monograph" 
  String date_monograph_published_print
  String date_monograph_published_online
  String monograph_volume
  String monograph_edition
  String first_editor
  String parent_publication_title_id
  String preceeding_publication_title_id
  String access_type //"P" or "F"
  String notes
  def additional_authors = []
  def additional_isbns = []
  def subjects = []
  
  String toString() {
    "($online_identifier, $print_identifier, $publication_title, $title_url, $first_author $additional_authors, $additional_isbns)"
  }
}
