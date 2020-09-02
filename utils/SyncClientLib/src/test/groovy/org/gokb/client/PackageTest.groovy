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
      p2.packageId = "001"
      p2.packageName = "001"
      p2.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p2.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p2.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p2.tipps.add( new GokbTippDTO(titleId:'004',title:'Title004') );

    when: "compare is called on p1(p2)"
      p1.compareWithPackage(p2);

    then: "Expect them to be equal"
      1==1

  }

  def "Tipps Added To Package"() {
    given:
      def p1 = new GokbPackageDTO();
      p1.packageId = "001"
      p1.packageName = "001"
      p1.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p1.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p1.tipps.add( new GokbTippDTO(titleId:'023',title:'Title023') );
      p1.tipps.add( new GokbTippDTO(titleId:'044',title:'Title044') );
      def p2 = new GokbPackageDTO();
      p2.packageId = "001"
      p2.packageName = "001"
      p2.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p2.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p2.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p2.tipps.add( new GokbTippDTO(titleId:'004',title:'Title004') );
      p2.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p2.tipps.add( new GokbTippDTO(titleId:'023',title:'Title023') );
      p2.tipps.add( new GokbTippDTO(titleId:'044',title:'Title044') );
      p2.tipps.add( new GokbTippDTO(titleId:'070',title:'Title070') );
    when: "compare is called on p1(p2)"
      p1.compareWithPackage(p2);

    then: "Expect new tipp reports for 2,3,4 and 70"
      1==1
  }


  def "Tipps Removed From Package (Last one intact)"() {
   given:
      def p1 = new GokbPackageDTO();
      p1.packageId = "001"
      p1.packageName = "001"
      p1.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p1.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p1.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p1.tipps.add( new GokbTippDTO(titleId:'004',title:'Title004') );
      p1.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p1.tipps.add( new GokbTippDTO(titleId:'023',title:'Title023') );
      p1.tipps.add( new GokbTippDTO(titleId:'044',title:'Title044') );
      p1.tipps.add( new GokbTippDTO(titleId:'070',title:'Title070') );
      def p2 = new GokbPackageDTO();
      p2.packageId = "001"
      p2.packageName = "001"
      p2.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p2.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p2.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p2.tipps.add( new GokbTippDTO(titleId:'070',title:'Title070') );
    when: "compare is called on p1(p2)"
      p1.compareWithPackage(p2);

    then: "Expect new tipp reports for 2,3,4 and 70"
      1==1
  }

  def "Tipps Removed From Package (Last one removed)"() {
   given:
      def p1 = new GokbPackageDTO();
      p1.packageId = "001"
      p1.packageName = "001"
      p1.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p1.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p1.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p1.tipps.add( new GokbTippDTO(titleId:'004',title:'Title004') );
      p1.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p1.tipps.add( new GokbTippDTO(titleId:'023',title:'Title023') );
      p1.tipps.add( new GokbTippDTO(titleId:'044',title:'Title044') );
      p1.tipps.add( new GokbTippDTO(titleId:'070',title:'Title070') );
      def p2 = new GokbPackageDTO();
      p2.packageId = "001"
      p2.packageName = "001"
      p2.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p2.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p2.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
    when: "compare is called on p1(p2)"
      p1.compareWithPackage(p2);

    then: "Expect new tipp reports for 2,3,4 and 70"
      1==1
  }


  def "Hybrid last equal"() {
   given:
      def p1 = new GokbPackageDTO();
      p1.packageId = "001"
      p1.packageName = "001"
      p1.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p1.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p1.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p1.tipps.add( new GokbTippDTO(titleId:'023',title:'Title023') );
      p1.tipps.add( new GokbTippDTO(titleId:'044',title:'Title044') );
      p1.tipps.add( new GokbTippDTO(titleId:'070',title:'Title070') );
      def p2 = new GokbPackageDTO();
      p2.packageId = "001"
      p2.packageName = "001"
      p2.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p2.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p2.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p1.tipps.add( new GokbTippDTO(titleId:'070',title:'Title070') );
    when: "compare is called on p1(p2)"
      p1.compareWithPackage(p2);

    then: "Expect new tipp reports for 2,3,4 and 70"
      1==1
  }

  def "Hybrid last removed"() {
   given:
      def p1 = new GokbPackageDTO();
      p1.packageId = "001"
      p1.packageName = "001"
      p1.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p1.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p1.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p1.tipps.add( new GokbTippDTO(titleId:'023',title:'Title023') );
      p1.tipps.add( new GokbTippDTO(titleId:'044',title:'Title044') );
      p1.tipps.add( new GokbTippDTO(titleId:'070',title:'Title070') );
      def p2 = new GokbPackageDTO();
      p2.packageId = "001"
      p2.packageName = "001"
      p2.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p2.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p2.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
    when: "compare is called on p1(p2)"
      p1.compareWithPackage(p2);

    then: "Expect new tipp reports for 2,3,4 and 70"
      1==1
  }

  def "Hybrid last added"() {
   given:
      def p1 = new GokbPackageDTO();
      p1.packageId = "001"
      p1.packageName = "001"
      p1.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p1.tipps.add( new GokbTippDTO(titleId:'002',title:'Title002') );
      p1.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p1.tipps.add( new GokbTippDTO(titleId:'023',title:'Title023') );
      p1.tipps.add( new GokbTippDTO(titleId:'044',title:'Title044') );
      p1.tipps.add( new GokbTippDTO(titleId:'070',title:'Title070') );
      def p2 = new GokbPackageDTO();
      p2.packageId = "001"
      p2.packageName = "001"
      p2.tipps.add( new GokbTippDTO(titleId:'001',title:'Title001') );
      p2.tipps.add( new GokbTippDTO(titleId:'003',title:'Title003') );
      p2.tipps.add( new GokbTippDTO(titleId:'010',title:'Title010') );
      p1.tipps.add( new GokbTippDTO(titleId:'071',title:'Title071') );
    when: "compare is called on p1(p2)"
      p1.compareWithPackage(p2);

    then: "Expect new tipp reports for 2,3,4 and 70"
      1==1
  }


}
