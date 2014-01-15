package org.gokb.client;

public class GokbPackageDTO {

  String packageId;
  String packageName;
  java.util.List<GokbTippDTO> tipps = new java.util.ArrayList();

  public String toString() {
    return packageId;
  }

  public String dump() {
    java.io.StringWriter sw = new java.io.StringWriter();
    sw.write("packageId:"+packageId+"\n");
    sw.write("packageName:"+packageName+"\n");
    for ( GokbTippDTO tipp : tipps ) {
      sw.write("    title: "+tipp.title+"\n");
    }
    return sw.toString();
  }
}
