<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:xs="http://www.w3.org/2001/XMLSchema" 
                xmlns:ev="http://www.w3.org/2001/xml-events" 
                xmlns:xforms="http://www.w3.org/2002/xforms" 
                xmlns="http://www.w3.org/1999/xhtml"
                xmlns:oxf="http://www.orbeon.com/oxf/processors"
                xmlns:onix="http://www.editeur.org/onix-pl"
                xmlns:ople="http://www.editeur.org/ople"
                xmlns:p="http://www.orbeon.com/oxf/pipeline"
                xmlns:saxon="http://saxon.sf.net/"
                xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext"
                xmlns:exist="http://exist.sourceforge.net/NS/exist"
                xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

  <xsl:output method="xhtml" exclude-result-prefixes="oxf xxforms context xs p exist ople saxon ev xforms onix"/>
  <xsl:variable name="apos">'</xsl:variable>

  <xsl:template match="/">
    <h1>
      <xsl:text>Summary of ONIX-PL expression of license "</xsl:text>
      <xsl:value-of select="//onix:LicenseDetail/onix:Description"/>
      <xsl:text>"</xsl:text>
    </h1>
    <xsl:apply-templates select="//onix:PublicationsLicenseExpression/onix:LicenseDetail"/>
    <xsl:apply-templates select="//onix:PublicationsLicenseExpression/onix:Definitions"/>
  </xsl:template>

  <xsl:template match="//onix:PublicationsLicenseExpression/onix:LicenseDetail">
    <xsl:value-of select="onix:Description"/>
    <xsl:apply-templates select="onix:LicenseStatus"/>
    <xsl:apply-templates select="onix:LicenseRenewalType"/>
    <xsl:apply-templates select="onix:LicenseIdentifier"/>
    <xsl:apply-templates select="onix:LicenseRelatedTimePoint"/>
    <xsl:apply-templates select="onix:LicenseRelatedPlace"/>

    License Document(s)
    <xsl:apply-templates select="onix:LicenseDocument"/>

  </xsl:template>
  

  <xsl:template match="onix:LicenseStatus">
    License Status : <xsl:value-of select="."/>
  </xsl:template>

  <xsl:template match="onix:LicenseRenewalType">
    License Renewal : <xsl:value-of select="."/>
  </xsl:template>

  <xsl:template match="onix:LicenseIdentifier">
    <xsl:value-of select="./onix:LicenseIDType"/> : <xsl:value-of select="./onix:IDValue"/>
  </xsl:template>

  <xsl:template match="onix:LicenseRelatedTimePoint">
    <xsl:value-of select="./onix:LicenseTimePointRelator"/> : <xsl:value-of select="./onix:RelatedTimePoint"/>
  </xsl:template>

  <xsl:template match="onix:LicenseRelatedPlace">
    <xsl:value-of select="./onix:LicensePlaceRelator"/> : <xsl:value-of select="./onix:RelatedPlace"/>
  </xsl:template>

  <xsl:template match="//onix:PublicationsLicenseExpression/onix:Definitions">
    <xsl:apply-templates select="onix:AgentDefinition"/>
    <xsl:apply-templates select="onix:ResourceDefinition"/>
  </xsl:template>

  <xsl:template match="onix:AgentDefinition">
    <xsl:value-of select="onix:AgentLabel"/>
    <xsl:value-of select="onix:AgentName/onix:Name"/>
    <xsl:value-of select="onix:AgentRelatedPlace"/>
  </xsl:template>

  <xsl:template match="onix:AgentRelatedPlace">
    <xsl:value-of select="onix:AgentPlaceRelator"/>
    <xsl:value-of select="onix:RelatedPlace"/>
  </xsl:template>
  
  <xsl:template match="onix:ResourceDefinition">
    <xsl:value-of select="onix:ResourceLabel"/>
    <xsl:value-of select="onix:Description"/>
    <xsl:apply-templates select="onix:LicenseTextLink"/>
  </xsl:template>

  <xsl:template match="onix:LicenseTextLink">
    <xsl:variable name="ref" select="./@href"/>
   *****LICENSE TEXT... <xsl:value-of select="$ref"/>
    <xsl:value-of select="//onix:PublicationsLicenseExpression/onix:LicenseDocumentText/onix:TextElement[@id=$ref]/../onix:DocumentLabel"/>
    <xsl:value-of select="//onix:PublicationsLicenseExpression/onix:LicenseDocumentText/onix:TextElement[@id=$ref]/onix:Text"/>
  </xsl:template>

  <xsl:template match="onix:LicenseDocument">
    <xsl:value-of select="./onix:LicenseDocumentType"/> : <xsl:value-of select="./onix:DocumentLabel"/>
  </xsl:template>

</xsl:stylesheet>
