package org.gokb.client;

import java.util.List;
import java.util.Iterator;

public class GokbPackageDTO implements java.io.Serializable {

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
      sw.write("    title["+tipp.titleId+"]: "+tipp.title+"\n");
    }

    return sw.toString();
  }

  public void compareWithPackage(GokbPackageDTO dto) {
    java.util.Collections.sort(tipps);
    java.util.Collections.sort(dto.tipps);

    Iterator ai = tipps.iterator();
    Iterator bi = dto.tipps.iterator();

    GokbTippDTO tippa = null;
    GokbTippDTO tippb = null;

    tippa = (GokbTippDTO) ( ai.hasNext() ? ai.next() : null );
    tippb = (GokbTippDTO) ( bi.hasNext() ? bi.next() : null );
    
    while ( tippa != null || tippb != null ) {

      System.out.println("Compare "+tippa+" and "+tippb);
      if ( tippa != null && 
           tippb != null && 
           tippa.compareTo(tippb) == 0 ) {
        System.out.println("  "+tippa.titleId+"    =    "+tippb.titleId);
        tippa = (GokbTippDTO) ( ai.hasNext() ? ai.next() : null );
        tippb = (GokbTippDTO) ( bi.hasNext() ? bi.next() : null );
      }
      else if ( ( tippb != null ) && 
                  ( ( tippa == null ) || 
                    ( tippa.compareTo(tippb) > 0 ) ) ) {
        System.out.println("Title "+tippb.titleId+" Was added to the package");
        tippb = (GokbTippDTO) ( bi.hasNext() ? bi.next() : null );
      }
      else {
        System.out.println("Title "+tippa.titleId+" Was removed from the package");
        tippa = (GokbTippDTO) ( ai.hasNext() ? ai.next() : null );
      }
    }
  }

}
