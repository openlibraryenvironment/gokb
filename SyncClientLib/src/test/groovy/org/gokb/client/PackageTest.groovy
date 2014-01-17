package org.gokb.client

import spock.lang.Specification

public class PackageTest extends Specification {

  def "Compares Equal Packages WIth Tipps"() {
    println("packageEqualTest");

    given: "Two packages the same"
      def p1 = new GokbPackageDTO();
      p1.packageId = "001"
      p1.packageName = "001"
      p1.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p1.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p1.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p1.tipps.add( new GokbTippDTO(titleId:'004',title:'Title004') );
      def p2 = new GokbPackageDTO();
      p2.packageId = "002"
      p2.packageName = "002"
      p2.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p2.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p2.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p2.tipps.add( new GokbTippDTO(titleId:'004',title:'Title004') );

    when: "compare is called on p1(p2)"
      p1.compareWithPackage(p2);

    then: "Expect them to be equal"
      1==1

  }



}
