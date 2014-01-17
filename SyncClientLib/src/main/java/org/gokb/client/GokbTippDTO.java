package org.gokb.client;

public class GokbTippDTO implements java.io.Serializable, java.lang.Comparable {

  String tippId;
  String pkgId;
  String pkgName;
  String platId;
  String platName;
  String title;
  String titleId;
  String startVolume;
  String startIssue;
  String startDate;
  String endVolume;
  String endIssue;
  String endDate;
  String coverageDepth;
  String coverageNote;

  public int compareTo(Object tipp) {
    return this.titleId.compareTo(((GokbTippDTO)tipp).titleId);
  }

  public String toString() {
    return titleId+":"+title;
  }
}
