<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

   <xsl:output method="text" encoding="utf-8"/>
   <xsl:strip-space elements="*" />

   <xsl:variable name="DOUBLEQUOTE">"</xsl:variable>
   <xsl:variable name="ESCAPEDDOUBLEQUOTE">XXXX</xsl:variable>
   <xsl:variable name="NONCE">¬</xsl:variable>

   <xsl:template match="/">
    <xsl:call-template name="ask_header" />
    <xsl:apply-templates select="//Book" />
   </xsl:template>

   <xsl:template match="Book">

      <!-- publication_title -->
      <xsl:call-template name="tsventry">
        <xsl:with-param name="txt" select="./Title" />
      </xsl:call-template>

      <!-- print_identifier -->
      <xsl:call-template name="tsventry">
        <xsl:with-param name="txt" select="./PrintISBN" />
      </xsl:call-template>

      <!-- online_identifier -->
      <xsl:call-template name="tsventry">
        <xsl:with-param name="txt" select="./eISBN" />
      </xsl:call-template>

      <!-- date_first_issue_online -->
      <xsl:call-template name="missing"/>

      <!-- num_first_vol_online -->
      <xsl:call-template name="missing"/>

      <!-- num_first_issue_online -->
      <xsl:call-template name="missing"/>

      <!-- date_last_issue_online -->
      <xsl:call-template name="missing"/>

      <!-- num_last_vol_online -->
      <xsl:call-template name="missing"/>

      <!-- num_last_issue_online -->
      <xsl:call-template name="missing"/>

      <!-- title_url -->
      <xsl:call-template name="tsventry">
        <xsl:with-param name="txt" select="./TitleURL" />
      </xsl:call-template>

      <!--  first_author -->
      <xsl:call-template name="missing"/>

      <!-- title_id -->
      <xsl:call-template name="missing"/>

      <!-- embargo_info -->
      <xsl:call-template name="missing"/>

      <!-- coverage_depth -->
      <xsl:call-template name="missing"/>

      <!-- notes -->
      <xsl:call-template name="missing"/>

      <!-- publisher_name -->
      <xsl:call-template name="tsventry">
        <xsl:with-param name="txt" select="./Publisher-name" />
      </xsl:call-template>

      <!-- publication_type -->
      <xsl:call-template name="missing"/>

      <!-- date_monograph_published_print -->
      <xsl:call-template name="missing"/>

      <!-- date_monograph_published_online -->
      <xsl:call-template name="missing"/>

      <!-- monograph_volume -->
      <xsl:call-template name="missing"/>

      <!-- monograph_edition -->
      <xsl:call-template name="missing"/>

      <!-- first_editor -->
      <xsl:call-template name="missing"/>

      <!-- parent_publication_title_id -->
      <xsl:call-template name="missing"/>

      <!-- preceding_publication_title_id -->
      <xsl:call-template name="missing"/>

      <!-- access_type -->
      <xsl:call-template name="missing"/>

      <!-- DOI -->
      <xsl:call-template name="missing"/>

      <!-- Title Image -->
      <xsl:call-template name="tsventry">
        <xsl:with-param name="txt" select="./ImageURL" />
      </xsl:call-template>

      <xsl:text>&#xA;</xsl:text>
  </xsl:template>

  <xsl:template name="missing"><xsl:text>&#x9;</xsl:text></xsl:template>
  
  <xsl:template name="tsventry">
    <xsl:param name="txt"/>
    <xsl:text>"</xsl:text>
    <xsl:value-of select="translate(normalize-space($txt),$DOUBLEQUOTE,$NONCE)"/>
    <xsl:text>"</xsl:text>
    <xsl:text>&#x9;</xsl:text>
  </xsl:template>

  <xsl:template name="plainentry"><xsl:param name="txt"/><xsl:value-of select="$txt"/></xsl:template>

  <xsl:template name="ask_header"><xsl:text>publication_title&#x9;print_identifier&#x9;online_identifier&#x9;date_first_issue_online&#x9;num_first_vol_online&#x9;num_first_issue_online&#x9;date_last_issue_online&#x9;num_last_vol_online&#x9;num_last_issue_online&#x9;title_url&#x9;first_author&#x9;title_id&#x9;embargo_info&#x9;coverage_depth&#x9;notes&#x9;publisher_name&#x9;publication_type&#x9;date_monograph_published_print&#x9;date_monograph_published_online&#x9;monograph_volume&#x9;monograph_edition&#x9;first_editor&#x9;parent_publication_title_id&#x9;preceding_publication_title_id&#x9;access_type&#x9;DOI&#x9;title_image&#xA;</xsl:text></xsl:template>


</xsl:stylesheet>

