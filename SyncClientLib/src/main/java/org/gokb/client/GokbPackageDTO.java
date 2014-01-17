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
    System.out.println("compare "+this.toString()+" with "+dto.toString());
    java.util.Collections.sort(tipps);
    java.util.Collections.sort(dto.tipps);

    Iterator ai = tipps.iterator();
    Iterator bi = dto.tipps.iterator();

    GokbTippDTO tippa = null;
    GokbTippDTO tippb = null;

    tippa = (GokbTippDTO) ( ai.hasNext() ? ai.next() : null );
    tippb = (GokbTippDTO) ( bi.hasNext() ? bi.next() : null );
    
    while ( tippa != null || tippb != null ) {
      if ( tippa != null && 
           tippb != null && 
           tippa.compareTo(tippb) == 0 ) {
        System.out.println("  "+tippa.titleId+"    =    "+tippb.titleId);
        tippa = (GokbTippDTO) ( ai.hasNext() ? ai.next() : null );
        tippb = (GokbTippDTO) ( bi.hasNext() ? bi.next() : null );
      }
      else if ( ( tippa.compareTo(tippb) > 0 ) || ( !ai.hasNext() ) ) {
        System.out.println("  "+tippa.titleId+"   = ");
        tippb = (GokbTippDTO) ( bi.hasNext() ? bi.next() : null );
      }
      else {
        System.out.println("             =    "+tippb.titleId);
        tippa = (GokbTippDTO) ( ai.hasNext() ? ai.next() : null );
      }
    }
  }

}
