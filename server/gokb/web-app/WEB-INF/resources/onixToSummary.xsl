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

  <xsl:output method="xhtml" 
              exclude-result-prefixes="oxf xxforms context xs p exist ople saxon ev xforms onix"/>

  <xsl:variable name="apos">'</xsl:variable>

  <xsl:template match="/">
    <h3>
      <xsl:text>Summary of ONIX-PL expression of license "</xsl:text>
      <xsl:value-of select="//onix:LicenseDetail/onix:Description"/>
      <xsl:text>"</xsl:text>
    </h3>
    <div class="container">
      <xsl:apply-templates select="//onix:PublicationsLicenseExpression/onix:LicenseDetail"/>
      <xsl:apply-templates select="//onix:PublicationsLicenseExpression/onix:Definitions"/>
    </div>
  </xsl:template>

  <xsl:template match="//onix:PublicationsLicenseExpression/onix:LicenseDetail">
    <dl class="dl-horizontal">

        <xsl:value-of select="onix:Description"/>
        <xsl:apply-templates select="onix:LicenseStatus"/>
        <xsl:apply-templates select="onix:LicenseRenewalType"/>
        <xsl:apply-templates select="onix:LicenseIdentifier"/>
        <xsl:apply-templates select="onix:LicenseRelatedTimePoint"/>
        <xsl:apply-templates select="onix:LicenseRelatedPlace"/>
    </dl>
    <h4>License Document(s)</h4>
    <dl class="dl-horizontal">
        <xsl:apply-templates select="onix:LicenseDocument"/>
    </dl>
  
  </xsl:template>
  

  <xsl:template match="onix:LicenseStatus">
    <div class="control-group">
      <dt>License Status</dt><dd><xsl:value-of select="."/></dd>
    </div>
  </xsl:template>

  <xsl:template match="onix:LicenseRenewalType">
    <div class="control-group">
      <dt>License Renewal</dt><dd><xsl:value-of select="."/></dd>
    </div>
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
    <div class="row">
      <div class="span6">
        <div class="control-group"><dt>Agent Label</dt><dd><xsl:value-of select="onix:AgentLabel"/></dd></div>
        <div class="control-group"><dt>Agent Name</dt><dd><xsl:value-of select="onix:AgentName/onix:Name"/></dd></div>
        <div class="control-group"><dt>Agent Place</dt><dd><xsl:value-of select="onix:AgentRelatedPlace"/></dd></div>
      </div>
      <div class="span6">
        <xsl:apply-templates select="onix:LicenseTextLink"/>
      </div>
    </div>
  </xsl:template>

  <xsl:template match="onix:AgentRelatedPlace">
    <div class="control-group"><dt><xsl:value-of select="onix:AgentPlaceRelator"/></dt><dd><xsl:value-of select="onix:RelatedPlace"/></dd></div>
  </xsl:template>
  
  <xsl:template match="onix:ResourceDefinition">
    <div class="row">
      <div class="span6">
        <div class="control-group"><dt><xsl:value-of select="onix:ResourceLabel"/></dt><dd><xsl:value-of select="onix:Description"/></dd></div>
      </div>
      <div class="span6">
        <xsl:apply-templates select="onix:LicenseTextLink"/>
      </div>
    </div>
  </xsl:template>

  <xsl:template match="onix:LicenseTextLink">
    <xsl:variable name="ref" select="./@href"/>
    <!-- xsl:value-of select="$ref" -->
    <xsl:value-of select="//onix:PublicationsLicenseExpression/onix:LicenseDocumentText/onix:TextElement[@id=$ref]/../onix:DocumentLabel"/><br/>
    <xsl:value-of select="//onix:PublicationsLicenseExpression/onix:LicenseDocumentText/onix:TextElement[@id=$ref]/onix:Text"/>
  </xsl:template>

  <xsl:template match="onix:LicenseDocument">
    <xsl:value-of select="./onix:LicenseDocumentType"/> : <xsl:value-of select="./onix:DocumentLabel"/>
  </xsl:template>

</xsl:stylesheet>
