package org.gokb.client;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;

@Entity
public class GokbTippDTO {

  @PrimaryKey
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

}
