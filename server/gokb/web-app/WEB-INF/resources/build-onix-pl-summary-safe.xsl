<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:xxforms="http://orbeon.org/oxf/xml/xforms" xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:p="http://www.orbeon.com/oxf/pipeline" xmlns:exist="http://exist.sourceforge.net/NS/exist" xmlns:ople="http://www.editeur.org/ople" xmlns:saxon="http://saxon.sf.net/" xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:xforms="http://www.w3.org/2002/xforms" xmlns="http://www.w3.org/1999/xhtml" xmlns:onix="http://www.editeur.org/onix-pl">
	<xsl:output method="xhtml" exclude-result-prefixes="oxf xxforms context xs p exist ople saxon ev xforms onix"/>
	<xsl:variable name="letters" select="'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'"/>
	<xsl:variable name="display-options" select="document('/views/summary-display-options.xml')"/>
	<xsl:variable name="info.png" select="'/views/info.png'"/>
	<xsl:variable name="tick.png" select="'/views/tick.png'"/>
	<xsl:variable name="cross.png" select="'/views/cross.png'"/>
	<xsl:variable name="config-url" select="'/config/ople-config.xml'"/>
	<xsl:variable name="config" select="document($config-url)"/>
	<xsl:variable name="exist-base-url" select="$config//property[@name='exist-base-url']/@value"/>
	<xsl:variable name="code-lists-url" select="concat($exist-base-url,'rest/db/ople1.0/code-lists.xml')"/>
	<xsl:variable name="code-lists" select="document($code-lists-url)"/>
	<xsl:variable name="apos">'</xsl:variable>
	<xsl:variable name="preposition">
		<Prepositions xmlns="">
			<Usage>
				<Type>onixPL:MakeDerivedWork</Type>
				<Value>from </Value>
			</Usage>
			<Usage>
				<Type>onixPL:MakeDigitalCopy</Type>
				<Value>of </Value>
			</Usage>
			<Usage>
				<Type>onixPL:MakeTemporaryDigitalCopy</Type>
				<Value>of </Value>
			</Usage>
			<Usage>
				<Type>onixPL:PrintCopy</Type>
				<Value>of </Value>
			</Usage>
			<Usage>
				<Type>onixPL:ProvideIntegratedAccess</Type>
				<Value>to </Value>
			</Usage>
			<Usage>
				<Type>onixPL:ProvideIntegratedIndex</Type>
				<Value>to </Value>
			</Usage>
			<Usage>
				<Type>onixPL:SupplyCopy</Type>
				<Value>of </Value>
			</Usage>
		</Prepositions>
	</xsl:variable>
	<xsl:variable name="valid-uri-schemes">
		<ValidURISchemes xmlns="">
			<Scheme>http</Scheme>
			<Scheme>ftp</Scheme>
			<Scheme>doi</Scheme>
		</ValidURISchemes>
	</xsl:variable>
	<xsl:template match="/">
		<html>
			<head>
				<title>
					<xsl:text>Summary of ONIX-PL expression of license "</xsl:text>
					<xsl:value-of select="//onix:LicenseDetail/onix:Description"/>
					<xsl:text>"</xsl:text>
				</title>
				<style type="text/css">
					body {
						font-family: verdana, arial, sans-serif;
						font-size: 100%;
					}
					.license-desc {
						font-size: 120%;
						font-weight: bold;
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.license-id {
						margin-top: 12pt;
					}
					.license-places {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.label {
						font-style: italic;
					}
					.license-download {
						text-decoration: none;
					}
					.license-annotation-type {
						font-style: italic;
					}
					.license-annotation-text {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.license-document {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.license-derivation {
						font-style: italic;
						text-align: right;
						margin-right: 12pt;
					}
					.license-model {
					}
					.license-model-version {
						font-size: 80%;
					}
					.quick-links {
						font-style: italic;
					}
					.more-info {
						font-style: italic;
					}
					.section-number {
						font-weight: normal;
						color: blue;
					}
					.license-extract {
						text-indent: 12pt;
					}
					.list-heading {
						font-size: 115%;
						font-weight: bold;
					}
					.sub-heading {
						font-style: italic;
						font-weight: bold;
					}
					.authority-label {
					    font-style: italic;
					    font-weight: normal;
					    color: black;
					}
					.authority {
					    font-weight: normal;
					}
					.agent-details {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.agent-places {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.resource-details {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.content-name {
						font-style: italic;
					}
					.document-details {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.usage {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.permitted {
						font-weight: bold;
						color: green;
					}
					.prohibited {
						font-weight: bold;
						color: red;
					}
					.code-value {
						font-weight: normal;
						color: maroon;
					}
					.definition-label {
						font-weight: normal;
						color: blue;
					}
					.relation {
						font-style: italic;
						font-weight: normal;
					}
					.SupplyTerm {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.ContinuingAccessTerm {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.PaymentTerm {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.GeneralTerm {
						margin-left: 24pt;
						text-indent: -24pt;
						margin-right: 24pt;
					}
					.other-terms {
						font-weight: bold;
						color: maroon;
					}
					.trigger {
						cursor: pointer;
						cursor: hand;
					}
					div.trigger:hover {
					    background-color: #ddd;
					}
					tr.trigger:hover {
					    background-color: #ddd;
					}
					.hidden {
						display: none;
					}
				</style>
				<script language="JavaScript">
					function showHidden(hidden) {
						var objHidden = 
							document.getElementById(hidden).style;
						if(objHidden.display=="block")
							objHidden.display="none"
						else
							objHidden.display="block";
					}
				</script>
			</head>
			<body>
				<script type="text/javascript" src="/views/wz_tooltip.js"/>
				<table cellpadding="0" cellspacing="0" width="100%">
					<colgroup>
						<col id="c1" width="4%"/>
						<col id="c2" width="24%"/>
						<col id="c3" width="22%"/>
						<col id="c4" width="4%"/>
						<col id="c5" width="46%"/>
					</colgroup>
					<tbody>
						<tr>
							<td>&#xA0;</td>
							<td>&#xA0;</td>
							<td>&#xA0;</td>
							<td>&#xA0;</td>
						</tr>
						<tr valign="top">
							<td colspan="3">
								<xsl:for-each select="//onix:LicenseDetail/onix:Description">
									<div class="license-desc">
										<xsl:value-of select="."/>
									</div>
								</xsl:for-each>
							</td>
							<td>&#xA0;</td>
							<td>&#xA0;</td>
						</tr>
						<tr valign="top">
							<td>&#xA0;</td>
							<td colspan="2">
								<div>
									<span class="label">
										<xsl:text>License status: </xsl:text>
									</span>
									<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='LicenseStatusCode']/ople:choice/ople:value[. = current()//onix:LicenseDetail/onix:LicenseStatus]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
									<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
										<xsl:call-template name="space-camel-case">
											<xsl:with-param name="in-string" select="substring-after(//onix:LicenseDetail/onix:LicenseStatus,':')"/>
										</xsl:call-template>
									</span>
								</div>
								<xsl:if test="//onix:LicenseDetail/onix:LicenseRenewalType">
									<div>
										<span class="label">
											<xsl:text>License renewal type: </xsl:text>
										</span>
										<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='LicenseRenewalTypeCode']/ople:choice/ople:value[. = current()//onix:LicenseDetail/onix:LicenseRenewalType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
										<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
											<xsl:call-template name="space-camel-case">
												<xsl:with-param name="in-string" select="substring-after(//onix:LicenseDetail/onix:LicenseRenewalType,':')"/>
											</xsl:call-template>
										</span>
									</div>
								</xsl:if>
								<xsl:for-each select="//onix:LicenseDetail/onix:LicenseIdentifier">
									<div>
										<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='LicenseIDTypeCode']/ople:choice/ople:value[. = current()/onix:LicenseIDType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
										<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
											<xsl:call-template name="space-camel-case">
												<xsl:with-param name="in-string" select="substring-after(onix:LicenseIDType,':')"/>
											</xsl:call-template>
											<xsl:text>: </xsl:text>
										</span>
										<xsl:value-of select="onix:IDValue"/>
									</div>
								</xsl:for-each>
								<div>
									<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedTimePoint">
										<div>
											<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='LicenseTimePointRelatorCode']/ople:choice/ople:value[. = current()/onix:LicenseTimePointRelator]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
											<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
												<xsl:call-template name="space-camel-case">
													<xsl:with-param name="in-string" select="substring-after(onix:LicenseTimePointRelator,':')"/>
												</xsl:call-template>
												<xsl:text>: </xsl:text>
											</span>
											<xsl:for-each select="onix:RelatedTimePoint[1]">
												<xsl:choose>
													<xsl:when test="contains(.,':')">
														<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedTimePointCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
														<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
															<xsl:call-template name="space-camel-case">
																<xsl:with-param name="in-string" select="substring-after(.,':')"/>
															</xsl:call-template>
														</span>
													</xsl:when>
													<xsl:when test="//onix:TimePointDefinition[onix:TimePointLabel=current()]">
														<span>
															<xsl:variable name="label-desc" select="translate(//onix:TimePointDefinition[onix:TimePointLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
															<xsl:if test="$label-desc != ''">
																<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
																<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
															</xsl:if>
															<xsl:choose>
																<xsl:when test="//onix:TimePointDefinition[onix:TimePointLabel=current()][1]/onix:TimePointIdentifier">
																	<xsl:value-of select="//onix:TimePointDefinition[onix:TimePointLabel=current()][1]/onix:TimePointIdentifier[1]/onix:IDValue"/>
																</xsl:when>
																<xsl:otherwise>
																	<xsl:attribute name="class">definition-label</xsl:attribute>
																	<xsl:value-of select="."/>
																</xsl:otherwise>
															</xsl:choose>
														</span>
													</xsl:when>
													<xsl:otherwise>
														<span style="color: black;">
															<xsl:value-of select="."/>
														</span>
													</xsl:otherwise>
												</xsl:choose>
											</xsl:for-each>
										</div>
									</xsl:for-each>
								</div>
								<xsl:if test="//onix:LicenseDetail/onix:LicenseRelatedPlace">
									<div class="license-places">
										<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedPlace">
											<div>
												<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='LicensePlaceRelatorCode']/ople:choice/ople:value[. = current()/onix:LicensePlaceRelator]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
												<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
													<xsl:call-template name="space-camel-case">
														<xsl:with-param name="in-string" select="substring-after(onix:LicensePlaceRelator,':')"/>
													</xsl:call-template>
													<xsl:text>: </xsl:text>
												</span>
												<xsl:for-each select="onix:RelatedPlace[1]">
													<xsl:variable name="label-desc" select="translate(//onix:PlaceDefinition[onix:PlaceLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
													<span>
														<xsl:if test="$label-desc != ''">
															<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
															<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
														</xsl:if>
														<xsl:for-each select="//onix:PlaceDefinition[onix:PlaceLabel=current()][1]">
															<xsl:choose>
																<xsl:when test="onix:PlaceName/onix:Name">
																	<xsl:value-of select="(onix:PlaceName/onix:Name)[1]"/>
																</xsl:when>
																<xsl:otherwise>
																	<xsl:attribute name="class">definition-label</xsl:attribute>
																	<xsl:value-of select="onix:PlaceLabel"/>
																</xsl:otherwise>
															</xsl:choose>
															<xsl:if test="onix:PlaceIdentifier">
																<span style="color: black;">
																	<xsl:text> (</xsl:text>
																	<xsl:value-of select="(onix:PlaceIdentifier/onix:IDValue)[1]"/>
																	<xsl:text>)</xsl:text>
																</span>
															</xsl:if>
														</xsl:for-each>
													</span>
												</xsl:for-each>
											</div>
										</xsl:for-each>
									</div>
								</xsl:if>
								<xsl:for-each select="//onix:LicenseDetail/onix:Annotation">
									<div class="license-annotation-text">
										<span class="license-annotation-type">
											<xsl:choose>
												<xsl:when test="onix:AnnotationType">
													<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='AnnotationTypeCode']/ople:choice/ople:value[. = current()/onix:AnnotationType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
													<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
														<xsl:call-template name="space-camel-case">
															<xsl:with-param name="in-string" select="substring-after(onix:AnnotationType,':')"/>
														</xsl:call-template>
														<xsl:text>: </xsl:text>
													</span>
												</xsl:when>
												<xsl:otherwise>
													<span class="label">Note: </span>
												</xsl:otherwise>
											</xsl:choose>
										</span>
										<xsl:value-of select="onix:AnnotationText"/>
										<xsl:if test="onix:Authority">
											<span class="authority">
												<xsl:text> (</xsl:text>
												<xsl:value-of select="onix:Authority"/>
												<xsl:text>)</xsl:text>
											</span>
										</xsl:if>
									</div>
								</xsl:for-each>
								<xsl:for-each select="//onix:ExpressionDetail/onix:Authority">
									<div>
										<span class="authority-label">Authority for license expression: </span>
										<span class="authority">
											<xsl:value-of select="."/>
										</span>
									</div>
								</xsl:for-each>
							</td>
							<td>&#xA0;</td>
							<td>&#xA0;</td>
						</tr>
						<xsl:if test="//onix:LicenseDetail/onix:LicenseDocument">
							<tr>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">
										<xsl:text>License Document(s)</xsl:text>
									</div>
								</td>
							</tr>
						</xsl:if>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseDocument">
							<tr valign="top">
								<td>&#xA0;</td>
								<td colspan="2">
									<div class="license-document">
										<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='LicenseDocumentTypeCode']/ople:choice/ople:value[. = current()/onix:LicenseDocumentType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
										<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
											<xsl:call-template name="space-camel-case">
												<xsl:with-param name="in-string" select="substring-after(onix:LicenseDocumentType,':')"/>
											</xsl:call-template>
											<xsl:text>: </xsl:text>
										</span>
										<xsl:for-each select="//onix:DocumentDefinition[onix:DocumentLabel = current()/onix:DocumentLabel]">
											<xsl:call-template name="document-details"/>
										</xsl:for-each>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
						</xsl:for-each>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator='onixPL:HasLicensor' or onix:LicenseAgentRelator='onixPL:Licensor']/onix:RelatedAgent">
							<xsl:variable name="licensor-id" select="concat('licensor-',generate-id(.))"/>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">
										<xsl:text>Licensor:</xsl:text>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
									<xsl:attribute name="class">trigger</xsl:attribute>
									<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$licensor-id"/>')</xsl:attribute>
								</xsl:if>
								<td>&#xA0;</td>
								<td colspan="2">
									<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]">
										<xsl:call-template name="agent-details"/>
									</xsl:for-each>
								</td>
								<td>
									<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
										<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
									</xsl:if>
								</td>
								<td>
									<div class="hidden" id="{$licensor-id}">
										<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
											<div class="license-extract">
												<xsl:call-template name="text-extract"/>
											</div>
										</xsl:for-each>
									</div>
								</td>
							</tr>
						</xsl:for-each>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator = 'onixPL:HasLicensee' or onix:LicenseAgentRelator='onixPL:Licensee']/onix:RelatedAgent">
							<xsl:variable name="licensee-id" select="concat('licensee-',generate-id())"/>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">
										<xsl:text>Licensee:</xsl:text>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
									<xsl:attribute name="class">trigger</xsl:attribute>
									<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$licensee-id"/>')</xsl:attribute>
								</xsl:if>
								<td>&#xA0;</td>
								<td colspan="2">
									<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]">
										<xsl:call-template name="agent-details"/>
									</xsl:for-each>
								</td>
								<td>
									<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
										<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
									</xsl:if>
								</td>
								<td>
									<div class="hidden" id="{$licensee-id}">
										<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
											<div class="license-extract">
												<xsl:call-template name="text-extract"/>
											</div>
										</xsl:for-each>
									</div>
								</td>
							</tr>
						</xsl:for-each>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedAgent[not(onix:LicenseAgentRelator='onixPL:HasLicensor' or onix:LicenseAgentRelator='onixPL:HasLicensee' or onix:LicenseAgentRelator='onixPL:HasAuthorizedUsers' or onix:LicenseAgentRelator='onixPL:Licensor' or onix:LicenseAgentRelator='onixPL:Licensee' or onix:LicenseAgentRelator='onixPL:AuthorizedUsers')]/onix:RelatedAgent">
							<xsl:variable name="agent-id" select="concat('agent-',generate-id())"/>
							<xsl:variable name="agent-relator">
								<xsl:value-of select="substring-after(../onix:LicenseAgentRelator,':')"/>
							</xsl:variable>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">
										<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='LicenseAgentRelatorCode']/ople:choice/ople:value[. = current()/../onix:LicenseAgentRelator]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
										<span onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
											<xsl:call-template name="space-camel-case">
												<xsl:with-param name="in-string" select="$agent-relator"/>
											</xsl:call-template>
										</span>
										<xsl:text>:</xsl:text>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
									<xsl:attribute name="class">trigger</xsl:attribute>
									<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$agent-id"/>')</xsl:attribute>
								</xsl:if>
								<td>&#xA0;</td>
								<td colspan="2">
									<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()]">
										<div class="licensee">
											<xsl:call-template name="agent-details"/>
										</div>
									</xsl:for-each>
								</td>
								<td>
									<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
										<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
									</xsl:if>
								</td>
								<td>
									<div class="hidden" id="{$agent-id}">
										<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
											<div class="license-extract">
												<xsl:call-template name="text-extract"/>
											</div>
										</xsl:for-each>
									</div>
								</td>
							</tr>
						</xsl:for-each>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedResource[onix:LicenseResourceRelator='onixPL:HasLicensedContent' or onix:LicenseResourceRelator='onixPL:LicensedContent']/onix:RelatedResource">
							<xsl:variable name="content-id" select="concat('content-',generate-id())"/>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">
										<xsl:text>Licensed Content:</xsl:text>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<xsl:if test="//onix:ResourceDefinition[onix:ResourceLabel=current()][1]/onix:LicenseTextLink">
									<xsl:attribute name="class">trigger</xsl:attribute>
									<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$content-id"/>')</xsl:attribute>
								</xsl:if>
								<td>&#xA0;</td>
								<td colspan="2">
									<xsl:call-template name="any-of-resources">
										<xsl:with-param name="resource-stack" select="concat('//',.,'//')"/>
									</xsl:call-template>
									<xsl:for-each select="//onix:ResourceDefinition[onix:AgentLabel=current()][1]/onix:Authority">
										<div>
											<span class="authority-label">Authority for definition: </span>
											<span class="authority">
												<xsl:value-of select="."/>
											</span>
										</div>
									</xsl:for-each>
								</td>
								<td>
									<xsl:if test="//onix:ResourceDefinition[onix:ResourceLabel=current()][1]/onix:LicenseTextLink">
										<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
									</xsl:if>
								</td>
								<td>
									<div class="hidden" id="{$content-id}">
										<xsl:for-each select="//onix:ResourceDefinition[onix:ResourceLabel=current()][1]/onix:LicenseTextLink">
											<div class="license-extract">
												<xsl:call-template name="text-extract"/>
											</div>
										</xsl:for-each>
									</div>
								</td>
							</tr>
						</xsl:for-each>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator='onixPL:HasAuthorizedUsers' or onix:LicenseAgentRelator='onixPL:AuthorizedUsers']/onix:RelatedAgent">
							<xsl:variable name="users-id" select="concat('users-',generate-id())"/>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">
										<xsl:text>Authorized Users:</xsl:text>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr valign="top">
								<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
									<xsl:attribute name="class">trigger</xsl:attribute>
									<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$users-id"/>')</xsl:attribute>
								</xsl:if>
								<td>&#xA0;</td>
								<td colspan="2">
									<div>
										<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]">
											<xsl:call-template name="agent-details"/>
										</xsl:for-each>
										<xsl:call-template name="any-of-users">
											<xsl:with-param name="user-stack" select="concat('//',.,'//')"/>
										</xsl:call-template>
									</div>
									<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:Authority">
										<div>
											<span class="authority-label">Authority for definition: </span>
											<span class="authority">
												<xsl:value-of select="."/>
											</span>
										</div>
									</xsl:for-each>
								</td>
								<td>
									<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
										<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
									</xsl:if>
								</td>
								<td>
									<div class="hidden" id="{$users-id}">
										<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:LicenseTextLink">
											<div class="license-extract">
												<xsl:call-template name="text-extract"/>
											</div>
										</xsl:for-each>
									</div>
								</td>
							</tr>
						</xsl:for-each>
						<tr>
							<td>&#xA0;</td>
						</tr>
						<tr valign="top">
							<td colspan="3">
								<div class="list-heading">What you <span class="permitted">may</span> do: </div>
							</td>
							<td>&#xA0;</td>
							<td>&#xA0;</td>
						</tr>
						<tr>
							<td>&#xA0;</td>
						</tr>
						<xsl:if test="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted']/onix:User='onixPL:Licensee'">
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UserCode']/ople:choice/ople:value[. = 'onixPL:Licensee']/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<tr>
								<td colspan="3">
									<div class="sub-heading">
										<xsl:text>As </xsl:text>
										<span style="color: maroon;" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">Licensee</span>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted') and onix:User='onixPL:Licensee']">
								<xsl:variable name="usage-id" select="concat('onixPL:Licensee-',generate-id())"/>
								<tr valign="top">
									<xsl:if test="onix:LicenseTextLink">
										<xsl:attribute name="class">trigger</xsl:attribute>
										<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
									</xsl:if>
									<td>
										<img alt="permitted" src="{$tick.png}"/>
									</td>
									<td colspan="2">
										<div class="usage">
											<xsl:call-template name="do-usage">
												<xsl:with-param name="status" select="'permitted'"/>
											</xsl:call-template>
										</div>
									</td>
									<td>
										<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
											<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
										</xsl:if>
									</td>
									<td>
										<div class="hidden" id="{$usage-id}">
											<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
												<div class="license-extract">
													<xsl:call-template name="text-extract"/>
												</div>
											</xsl:for-each>
										</div>
									</td>
								</tr>
							</xsl:for-each>
							<tr>
								<td>&#xA0;</td>
							</tr>
						</xsl:if>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator='onixPL:Licensee']/onix:RelatedAgent">
							<xsl:if test="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted']/onix:User=current()">
								<tr>
									<td colspan="3">
										<div class="sub-heading">
											<xsl:text>As </xsl:text>
											<span style="color: blue;">
												<xsl:variable name="label-desc" select="translate(//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
												<xsl:if test="$label-desc!=''">
													<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
													<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
												</xsl:if>
												<xsl:value-of select="."/>
											</span>
										</div>
									</td>
									<td>&#xA0;</td>
									<td>&#xA0;</td>
								</tr>
								<tr>
									<td>&#xA0;</td>
								</tr>
								<xsl:variable name="user" select="."/>
								<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted') and onix:User=$user]">
									<xsl:variable name="usage-id" select="concat($user,'-',generate-id())"/>
									<tr valign="top">
										<xsl:if test="onix:LicenseTextLink">
											<xsl:attribute name="class">trigger</xsl:attribute>
											<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
										</xsl:if>
										<td>
											<img alt="permitted" src="{$tick.png}"/>
										</td>
										<td colspan="2">
											<div class="usage">
												<xsl:call-template name="do-usage">
													<xsl:with-param name="status" select="'permitted'"/>
												</xsl:call-template>
											</div>
										</td>
										<td>
											<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
												<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
											</xsl:if>
										</td>
										<td>
											<div class="hidden" id="{$usage-id}">
												<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
													<div class="license-extract">
														<xsl:call-template name="text-extract"/>
													</div>
												</xsl:for-each>
											</div>
										</td>
									</tr>
								</xsl:for-each>
								<tr>
									<td>&#xA0;</td>
								</tr>
							</xsl:if>
						</xsl:for-each>
						<xsl:if test="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted']/onix:User='onixPL:AuthorizedUser'">
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UserCode']/ople:choice/ople:value[. = 'onixPL:AuthorizedUser']/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<tr>
								<td colspan="3">
									<div class="sub-heading">
										<xsl:text>As </xsl:text>
										<span style="color: maroon;" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">Authorized User</span>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted') and onix:User='onixPL:AuthorizedUser']">
								<xsl:variable name="usage-id" select="concat('onixPL:AuthorizedUser-',generate-id())"/>
								<tr valign="top">
									<xsl:if test="onix:LicenseTextLink">
										<xsl:attribute name="class">trigger</xsl:attribute>
										<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
									</xsl:if>
									<td>
										<img alt="permitted" src="{$tick.png}"/>
									</td>
									<td colspan="2">
										<div class="usage">
											<xsl:call-template name="do-usage">
												<xsl:with-param name="status" select="'permitted'"/>
											</xsl:call-template>
										</div>
									</td>
									<td>
										<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
											<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
										</xsl:if>
									</td>
									<td>
										<div class="hidden" id="{$usage-id}">
											<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
												<div class="license-extract">
													<xsl:call-template name="text-extract"/>
												</div>
											</xsl:for-each>
										</div>
									</td>
								</tr>
							</xsl:for-each>
							<tr>
								<td>&#xA0;</td>
							</tr>
						</xsl:if>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator='onixPL:AuthorizedUsers']/onix:RelatedAgent">
							<xsl:if test="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted']/onix:User=current()">
								<tr>
									<td colspan="3">
										<div class="sub-heading">
											<xsl:text>As </xsl:text>
											<span style="color: blue;">
												<xsl:variable name="label-desc" select="translate(//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
												<xsl:if test="$label-desc!=''">
													<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
													<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
												</xsl:if>
												<xsl:value-of select="."/>
											</span>
										</div>
									</td>
									<td>&#xA0;</td>
									<td>&#xA0;</td>
								</tr>
								<tr>
									<td>&#xA0;</td>
								</tr>
								<xsl:variable name="user" select="."/>
								<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted') and onix:User=$user]">
									<xsl:variable name="usage-id" select="concat($user,'-',generate-id())"/>
									<tr valign="top">
										<xsl:if test="onix:LicenseTextLink">
											<xsl:attribute name="class">trigger</xsl:attribute>
											<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
										</xsl:if>
										<td>
											<img alt="permitted" src="{$tick.png}"/>
										</td>
										<td colspan="2">
											<div class="usage">
												<xsl:call-template name="do-usage">
													<xsl:with-param name="status" select="'permitted'"/>
												</xsl:call-template>
											</div>
										</td>
										<td>
											<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
												<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
											</xsl:if>
										</td>
										<td>
											<div class="hidden" id="{$usage-id}">
												<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
													<div class="license-extract">
														<xsl:call-template name="text-extract"/>
													</div>
												</xsl:for-each>
											</div>
										</td>
									</tr>
								</xsl:for-each>
								<tr>
									<td>&#xA0;</td>
								</tr>
							</xsl:if>
						</xsl:for-each>
						<xsl:for-each select="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted']/onix:User[contains(.,':') and not(.='onixPL:Licensee' or .='onixPL:AuthorizedUser')]">
							<xsl:variable name="user" select="."/>
							<xsl:if test="not(../preceding-sibling::onix:Usage[(onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted') and onix:User=$user])">
								<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UserCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
								<tr>
									<td colspan="3">
										<div class="sub-heading">
											<xsl:text>As </xsl:text>
											<span style="color: maroon;" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
												<xsl:call-template name="space-camel-case">
													<xsl:with-param name="in-string" select="substring-after(.,':')"/>
												</xsl:call-template>
											</span>
										</div>
									</td>
									<td>&#xA0;</td>
									<td>&#xA0;</td>
								</tr>
								<tr>
									<td>&#xA0;</td>
								</tr>
								<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted') and onix:User=$user]">
									<xsl:variable name="usage-id" select="concat($user,'-',generate-id())"/>
									<tr valign="top">
										<xsl:if test="onix:LicenseTextLink">
											<xsl:attribute name="class">trigger</xsl:attribute>
											<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
										</xsl:if>
										<td>
											<img alt="permitted" src="{$tick.png}"/>
										</td>
										<td colspan="2">
											<div class="usage">
												<xsl:call-template name="do-usage">
													<xsl:with-param name="status" select="'permitted'"/>
												</xsl:call-template>
											</div>
										</td>
										<td>
											<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
												<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
											</xsl:if>
										</td>
										<td>
											<div class="hidden" id="{$usage-id}">
												<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
													<div class="license-extract">
														<xsl:call-template name="text-extract"/>
													</div>
												</xsl:for-each>
											</div>
										</td>
									</tr>
								</xsl:for-each>
								<tr>
									<td>&#xA0;</td>
								</tr>
							</xsl:if>
						</xsl:for-each>
						<xsl:for-each select="//onix:AgentDefinition[not(onix:AgentLabel = //onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator='onixPL:Licensee' or onix:LicenseAgentRelator='onixPL:AuthorizedUsers']/onix:RelatedAgent)]">
							<xsl:if test="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted') and onix:User=current()/onix:AgentLabel]">
								<tr>
									<td colspan="3">
										<div class="sub-heading">
											<xsl:text>As </xsl:text>
											<span style="color: blue;">
												<xsl:variable name="label-desc" select="translate(onix:Description,$apos,'&#x2019;')"/>
												<xsl:if test="$label-desc!=''">
													<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
													<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
												</xsl:if>
												<xsl:value-of select="onix:AgentLabel"/>
											</span>
										</div>
									</td>
								</tr>
								<tr>
									<td>&#xA0;</td>
								</tr>
								<xsl:variable name="user" select="onix:AgentLabel"/>
								<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Permitted' or onix:UsageStatus='onixPL:InterpretedAsPermitted') and onix:User=$user]">
									<xsl:variable name="usage-id" select="concat($user,'-',generate-id())"/>
									<tr valign="top">
										<xsl:if test="onix:LicenseTextLink">
											<xsl:attribute name="class">trigger</xsl:attribute>
											<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
										</xsl:if>
										<td>
											<img alt="permitted" src="{$tick.png}"/>
										</td>
										<td colspan="2">
											<div class="usage">
												<xsl:call-template name="do-usage">
													<xsl:with-param name="status" select="'permitted'"/>
												</xsl:call-template>
											</div>
										</td>
										<td>
											<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
												<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
											</xsl:if>
										</td>
										<td>
											<div class="hidden" id="{$usage-id}">
												<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
													<div class="license-extract">
														<xsl:call-template name="text-extract"/>
													</div>
												</xsl:for-each>
											</div>
										</td>
									</tr>
								</xsl:for-each>
								<tr>
									<td>&#xA0;</td>
								</tr>
							</xsl:if>
						</xsl:for-each>
						<tr valign="top">
							<td colspan="3">
								<div class="list-heading">What you <span class="prohibited">may not</span> do: </div>
							</td>
							<td>&#xA0;</td>
							<td>&#xA0;</td>
						</tr>
						<tr>
							<td>&#xA0;</td>
						</tr>
						<xsl:if test="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited']/onix:User='onixPL:Licensee'">
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UserCode']/ople:choice/ople:value[. = 'onixPL:Licensee']/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<tr>
								<td colspan="3">
									<div class="sub-heading">
										<xsl:text>As </xsl:text>
										<span style="color: maroon;" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">Licensee</span>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited') and onix:User='onixPL:Licensee']">
								<xsl:variable name="usage-id" select="concat('onixPL:Licensee-',generate-id())"/>
								<tr valign="top">
									<xsl:if test="onix:LicenseTextLink">
										<xsl:attribute name="class">trigger</xsl:attribute>
										<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
									</xsl:if>
									<td>
										<img alt="prohibited" src="{$cross.png}"/>
									</td>
									<td colspan="2">
										<div class="usage">
											<xsl:call-template name="do-usage">
												<xsl:with-param name="status" select="'prohibited'"/>
											</xsl:call-template>
										</div>
									</td>
									<td>
										<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
											<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
										</xsl:if>
									</td>
									<td>
										<div class="hidden" id="{$usage-id}">
											<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
												<div class="license-extract">
													<xsl:call-template name="text-extract"/>
												</div>
											</xsl:for-each>
										</div>
									</td>
								</tr>
							</xsl:for-each>
							<tr>
								<td>&#xA0;</td>
							</tr>
						</xsl:if>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator='onixPL:Licensee']/onix:RelatedAgent">
							<xsl:if test="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited']/onix:User=current()">
								<tr>
									<td colspan="3">
										<div class="sub-heading">
											<xsl:text>As </xsl:text>
											<span style="color: blue;">
												<xsl:variable name="label-desc" select="translate(//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
												<xsl:if test="$label-desc!=''">
													<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
													<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
												</xsl:if>
												<xsl:value-of select="."/>
											</span>
										</div>
									</td>
									<td>&#xA0;</td>
									<td>&#xA0;</td>
								</tr>
								<tr>
									<td>&#xA0;</td>
								</tr>
								<xsl:variable name="user" select="."/>
								<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited') and onix:User=$user]">
									<xsl:variable name="usage-id" select="concat($user,'-',generate-id())"/>
									<tr valign="top">
										<xsl:if test="onix:LicenseTextLink">
											<xsl:attribute name="class">trigger</xsl:attribute>
											<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
										</xsl:if>
										<td>
											<img alt="prohibited" src="{$cross.png}"/>
										</td>
										<td colspan="2">
											<div class="usage">
												<xsl:call-template name="do-usage">
													<xsl:with-param name="status" select="'prohibited'"/>
												</xsl:call-template>
											</div>
										</td>
										<td>
											<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
												<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
											</xsl:if>
										</td>
										<td>
											<div class="hidden" id="{$usage-id}">
												<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
													<div class="license-extract">
														<xsl:call-template name="text-extract"/>
													</div>
												</xsl:for-each>
											</div>
										</td>
									</tr>
								</xsl:for-each>
								<tr>
									<td>&#xA0;</td>
								</tr>
							</xsl:if>
						</xsl:for-each>
						<xsl:if test="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited']/onix:User='onixPL:AuthorizedUser'">
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UserCode']/ople:choice/ople:value[. = 'onixPL:AuthorizedUser']/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<tr>
								<td colspan="3">
									<div class="sub-heading">
										<xsl:text>As </xsl:text>
										<span style="color: maroon;" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">Authorized User</span>
									</div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited') and onix:User='onixPL:AuthorizedUser']">
								<xsl:variable name="usage-id" select="concat('onixPL:AuthorizedUser-',generate-id())"/>
								<tr valign="top">
									<xsl:if test="onix:LicenseTextLink">
										<xsl:attribute name="class">trigger</xsl:attribute>
										<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
									</xsl:if>
									<td>
										<img alt="prohibited" src="{$cross.png}"/>
									</td>
									<td colspan="2">
										<div class="usage">
											<xsl:call-template name="do-usage">
												<xsl:with-param name="status" select="'prohibited'"/>
											</xsl:call-template>
										</div>
									</td>
									<td>
										<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
											<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
										</xsl:if>
									</td>
									<td>
										<div class="hidden" id="{$usage-id}">
											<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
												<div class="license-extract">
													<xsl:call-template name="text-extract"/>
												</div>
											</xsl:for-each>
										</div>
									</td>
								</tr>
							</xsl:for-each>
							<tr>
								<td>&#xA0;</td>
							</tr>
						</xsl:if>
						<xsl:for-each select="//onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator='onixPL:AuthorizedUsers']/onix:RelatedAgent">
							<xsl:if test="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited']/onix:User=current()">
								<tr>
									<td colspan="3">
										<div class="sub-heading">
											<xsl:text>As </xsl:text>
											<span style="color: blue;">
												<xsl:variable name="label-desc" select="translate(//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
												<xsl:if test="$label-desc!=''">
													<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
													<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
												</xsl:if>
												<xsl:value-of select="."/>
											</span>
										</div>
									</td>
									<td>&#xA0;</td>
									<td>&#xA0;</td>
								</tr>
								<tr>
									<td>&#xA0;</td>
								</tr>
								<xsl:variable name="user" select="."/>
								<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited') and onix:User=$user]">
									<xsl:variable name="usage-id" select="concat($user,'-',generate-id())"/>
									<tr valign="top">
										<xsl:if test="onix:LicenseTextLink">
											<xsl:attribute name="class">trigger</xsl:attribute>
											<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
										</xsl:if>
										<td>
											<img alt="prohibited" src="{$cross.png}"/>
										</td>
										<td colspan="2">
											<div class="usage">
												<xsl:call-template name="do-usage">
													<xsl:with-param name="status" select="'prohibited'"/>
												</xsl:call-template>
											</div>
										</td>
										<td>
											<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
												<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
											</xsl:if>
										</td>
										<td>
											<div class="hidden" id="{$usage-id}">
												<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
													<div class="license-extract">
														<xsl:call-template name="text-extract"/>
													</div>
												</xsl:for-each>
											</div>
										</td>
									</tr>
								</xsl:for-each>
								<tr>
									<td>&#xA0;</td>
								</tr>
							</xsl:if>
						</xsl:for-each>
						<xsl:for-each select="//onix:UsageTerms/onix:Usage[onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited']/onix:User[contains(.,':') and not(.='onixPL:Licensee' or .='onixPL:AuthorizedUser')]">
							<xsl:variable name="user" select="."/>
							<xsl:if test="not(../preceding-sibling::onix:Usage[(onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited') and onix:User=$user])">
								<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UserCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
								<tr>
									<td colspan="3">
										<div class="sub-heading">
											<xsl:text>As </xsl:text>
											<span style="color: maroon;" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
												<xsl:call-template name="space-camel-case">
													<xsl:with-param name="in-string" select="substring-after(.,':')"/>
												</xsl:call-template>
											</span>
										</div>
									</td>
									<td>&#xA0;</td>
									<td>&#xA0;</td>
								</tr>
								<tr>
									<td>&#xA0;</td>
								</tr>
								<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited') and onix:User=$user]">
									<xsl:variable name="usage-id" select="concat($user,'-',generate-id())"/>
									<tr valign="top">
										<xsl:if test="onix:LicenseTextLink">
											<xsl:attribute name="class">trigger</xsl:attribute>
											<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
										</xsl:if>
										<td>
											<img alt="prohibited" src="{$cross.png}"/>
										</td>
										<td colspan="2">
											<div class="usage">
												<xsl:call-template name="do-usage">
													<xsl:with-param name="status" select="'prohibited'"/>
												</xsl:call-template>
											</div>
										</td>
										<td>
											<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
												<div class="more-info trigger"><img alt="More information" src="{$info.png}"/></div>
											</xsl:if>
										</td>
										<td>
											<div class="hidden" id="{$usage-id}">
												<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
													<div class="license-extract">
														<xsl:call-template name="text-extract"/>
													</div>
												</xsl:for-each>
											</div>
										</td>
									</tr>
								</xsl:for-each>
								<tr>
									<td>&#xA0;</td>
								</tr>
							</xsl:if>
						</xsl:for-each>
						<xsl:for-each select="//onix:AgentDefinition[not(onix:AgentLabel = //onix:LicenseDetail/onix:LicenseRelatedAgent[onix:LicenseAgentRelator='onixPL:Licensee' or onix:LicenseAgentRelator='onixPL:AuthorizedUsers']/onix:RelatedAgent)]">
							<xsl:if test="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited') and onix:User=current()/onix:AgentLabel]">
								<tr>
									<td colspan="3">
										<div class="sub-heading">
											<xsl:text>As </xsl:text>
											<span style="color: blue;">
												<xsl:variable name="label-desc" select="translate(onix:Description,$apos,'&#x2019;')"/>
												<xsl:if test="$label-desc!=''">
													<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
													<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
												</xsl:if>
												<xsl:value-of select="onix:AgentLabel"/>
											</span>
										</div>
									</td>
									<td>&#xA0;</td>
									<td>&#xA0;</td>
								</tr>
								<tr>
									<td>&#xA0;</td>
								</tr>
								<xsl:variable name="user" select="onix:AgentLabel"/>
								<xsl:for-each select="//onix:UsageTerms/onix:Usage[(onix:UsageStatus='onixPL:Prohibited' or onix:UsageStatus='onixPL:InterpretedAsProhibited') and onix:User=$user]">
									<xsl:variable name="usage-id" select="concat($user,'-',generate-id())"/>
									<tr valign="top">
										<xsl:if test="onix:LicenseTextLink">
											<xsl:attribute name="class">trigger</xsl:attribute>
											<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$usage-id"/>')</xsl:attribute>
										</xsl:if>
										<td>
											<img alt="prohibited" src="{$cross.png}"/>
										</td>
										<td colspan="2">
											<div class="usage">
												<xsl:call-template name="do-usage">
													<xsl:with-param name="status" select="'prohibited'"/>
												</xsl:call-template>
											</div>
										</td>
										<td>
											<xsl:if test="onix:LicenseTextLink or onix:UsageException/onix:LicenseTextLink">
												<div class="more-info trigger"><img alt="More information" src="{$info.png}"/></div>
											</xsl:if>
										</td>
										<td>
											<div class="hidden" id="{$usage-id}">
												<xsl:for-each select="onix:LicenseTextLink | onix:UsageException/onix:LicenseTextLink">
													<div class="license-extract">
														<xsl:call-template name="text-extract"/>
													</div>
												</xsl:for-each>
											</div>
										</td>
									</tr>
								</xsl:for-each>
								<tr>
									<td>&#xA0;</td>
								</tr>
							</xsl:if>
						</xsl:for-each>
					</tbody>
					<xsl:if test="//onix:SupplyTerms/onix:SupplyTerm and ($display-options//DisplayOptions/@display='all' or $display-options//SupplyTerms/@display!='none')">
						<tbody>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">Supply-related terms: </div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<xsl:choose>
								<xsl:when test="$display-options//DisplayOptions/@display='selective' and $display-options//SupplyTerms/@display='selective'">
									<xsl:for-each select="//onix:SupplyTerms/onix:SupplyTerm">
										<xsl:if test="$display-options//SupplyTerms/SupplyTermType=current()/onix:SupplyTermType">
											<xsl:call-template name="do-other-terms">
												<xsl:with-param name="terms-type" select="'SupplyTerm'"/>
											</xsl:call-template>
										</xsl:if>
									</xsl:for-each>
								</xsl:when>
								<xsl:otherwise>
									<xsl:for-each select="//onix:SupplyTerms/onix:SupplyTerm">
										<xsl:call-template name="do-other-terms">
											<xsl:with-param name="terms-type" select="'SupplyTerm'"/>
										</xsl:call-template>
									</xsl:for-each>
								</xsl:otherwise>
							</xsl:choose>
							<tr>
								<td>&#xA0;</td>
							</tr>
						</tbody>
					</xsl:if>
					<xsl:if test="//onix:ContinuingAccessTerms/onix:ContinuingAccessTerm and ($display-options//DisplayOptions/@display='all' or $display-options//ContinuingAccessTerms/@display!='none')">
						<tbody>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">Continuing access terms: </div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<xsl:choose>
								<xsl:when test="$display-options//DisplayOptions/@display='selective' and $display-options//ContinuingAccessTerms/@display='selective'">
									<xsl:for-each select="//onix:ContinuingAccessTerms/onix:ContinuingAccessTerm">
										<xsl:if test="$display-options//ContinuingAccessTerms/ContinuingAccessTermType=current()/onix:ContinuingAccessTermType">
											<xsl:call-template name="do-other-terms">
												<xsl:with-param name="terms-type" select="'ContinuingAccessTerm'"/>
											</xsl:call-template>
										</xsl:if>
									</xsl:for-each>
								</xsl:when>
								<xsl:otherwise>
									<xsl:for-each select="//onix:ContinuingAccessTerms/onix:ContinuingAccessTerm">
										<xsl:call-template name="do-other-terms">
											<xsl:with-param name="terms-type" select="'ContinuingAccessTerm'"/>
										</xsl:call-template>
									</xsl:for-each>
								</xsl:otherwise>
							</xsl:choose>
							<tr>
								<td>&#xA0;</td>
							</tr>
						</tbody>
					</xsl:if>
					<xsl:if test="//onix:PaymentTerms/onix:PaymentTerm and ($display-options//DisplayOptions/@display='all' or $display-options//PaymentTerms/@display!='none')">
						<tbody>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">Payment terms: </div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<xsl:choose>
								<xsl:when test="$display-options//DisplayOptions/@display='selective' and $display-options//PaymentTerms/@display='selective'">
									<xsl:for-each select="//onix:PaymentTerms/onix:PaymentTerm">
										<xsl:if test="$display-options//PaymentTerms/PaymentTermType=current()/onix:PaymentTermType">
											<xsl:call-template name="do-other-terms">
												<xsl:with-param name="terms-type" select="'PaymentTerm'"/>
											</xsl:call-template>
										</xsl:if>
									</xsl:for-each>
								</xsl:when>
								<xsl:otherwise>
									<xsl:for-each select="//onix:PaymentTerms/onix:PaymentTerm">
										<xsl:call-template name="do-other-terms">
											<xsl:with-param name="terms-type" select="'PaymentTerm'"/>
										</xsl:call-template>
									</xsl:for-each>
								</xsl:otherwise>
							</xsl:choose>
							<tr>
								<td>&#xA0;</td>
							</tr>
						</tbody>
					</xsl:if>
					<xsl:if test="//onix:GeneralTerms/onix:GeneralTerm and ($display-options//DisplayOptions/@display='all' or $display-options//GeneralTerms/@display!='none')">
						<tbody>
							<tr valign="top">
								<td colspan="3">
									<div class="list-heading">Other license terms and conditions: </div>
								</td>
								<td>&#xA0;</td>
								<td>&#xA0;</td>
							</tr>
							<tr>
								<td>&#xA0;</td>
							</tr>
							<xsl:choose>
								<xsl:when test="$display-options//DisplayOptions/@display='selective' and $display-options//GeneralTerms/@display='selective'">
									<xsl:for-each select="//onix:GeneralTerms/onix:GeneralTerm">
										<xsl:if test="$display-options//GeneralTerms/GeneralTermType=current()/onix:GeneralTermType">
											<xsl:call-template name="do-other-terms">
												<xsl:with-param name="terms-type" select="'GeneralTerm'"/>
											</xsl:call-template>
										</xsl:if>
									</xsl:for-each>
								</xsl:when>
								<xsl:otherwise>
									<xsl:for-each select="//onix:GeneralTerms/onix:GeneralTerm">
										<xsl:call-template name="do-other-terms">
											<xsl:with-param name="terms-type" select="'GeneralTerm'"/>
										</xsl:call-template>
									</xsl:for-each>
								</xsl:otherwise>
							</xsl:choose>
						</tbody>
					</xsl:if>
				</table>
			</body>
		</html>
	</xsl:template>
	<xsl:template name="any-of-users">
		<xsl:param name="user-stack"/>
		<xsl:for-each select="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:AgentRelatedAgent[onix:AgentAgentRelator='onixPL:IsAnyOf']/onix:RelatedAgent">
			<div>
				<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedAgentCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
				<xsl:choose>
					<xsl:when test="contains(.,':')">
						<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string">
									<xsl:value-of select="substring-after(.,':')"/>
								</xsl:with-param>
							</xsl:call-template>
						</span>
					</xsl:when>
					<xsl:otherwise>
						<span class="definition-label">
							<xsl:variable name="label-desc" select="translate(//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
							<xsl:if test="$label-desc != ''">
								<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
								<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
							</xsl:if>
							<xsl:value-of select="."/>
						</span>
						<xsl:if test="//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:AgentRelatedAgent[onix:AgentAgentRelator='onixPL:IsAnyOf'] and not(contains($user-stack,concat('//',.,'//')))">
							<div style="margin-left: 48pt">
								<xsl:call-template name="any-of-users">
									<xsl:with-param name="user-stack" select="concat($user-stack,.,'//')"/>
								</xsl:call-template>
							</div>
						</xsl:if>
					</xsl:otherwise>
				</xsl:choose>
			</div>
		</xsl:for-each>
	</xsl:template>
	<xsl:template name="agent-details">
		<xsl:if test="onix:AgentName">
			<div class="agent-details">
				<span>
					<xsl:for-each select="onix:AgentName[1]">
						<xsl:value-of select="onix:Name"/>
						<xsl:if test="onix:AgentNameType">
							<xsl:text> (</xsl:text>
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='AgentNameTypeCode']/ople:choice/ople:value[. = current()/onix:AgentNameType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(onix:AgentNameType,':')"/>
								</xsl:call-template>
							</span>
							<xsl:text>)</xsl:text>
						</xsl:if>
					</xsl:for-each>
					<xsl:for-each select="onix:AgentName[position() != 1]">
						<xsl:text> / </xsl:text>
						<xsl:value-of select="onix:Name"/>
						<xsl:if test="onix:AgentNameType">
							<xsl:text> (</xsl:text>
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='AgentNameTypeCode']/ople:choice/ople:value[. = current()/onix:AgentNameType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(onix:AgentNameType,':')"/>
								</xsl:call-template>
							</span>
							<xsl:text>)</xsl:text>
						</xsl:if>
					</xsl:for-each>
				</span>
			</div>
		</xsl:if>
		<xsl:for-each select="onix:AgentIdentifier">
			<div class="agent-details">
				<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='AgentIDTypeCode']/ople:choice/ople:value[. = current()/onix:AgentIDType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
				<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
					<xsl:call-template name="space-camel-case">
						<xsl:with-param name="in-string" select="substring-after(onix:AgentIDType,':')"/>
					</xsl:call-template>
					<xsl:text>: </xsl:text>
				</span>
				<xsl:value-of select="onix:IDValue"/>
			</div>
		</xsl:for-each>
		<xsl:if test="onix:Description">
			<div class="agent-details">
				<span class="label">Description: </span>
				<xsl:value-of select="onix:Description"/>
			</div>
		</xsl:if>
		<xsl:call-template name="notes-and-references"/>
		<xsl:for-each select="onix:AgentRelatedPlace">
			<div class="agent-details">
				<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='AgentPlaceRelatorCode']/ople:choice/ople:value[. = current()/onix:AgentPlaceRelator]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
				<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
					<xsl:call-template name="space-camel-case">
						<xsl:with-param name="in-string">
							<xsl:value-of select="substring-after(onix:AgentPlaceRelator,':')"/>
						</xsl:with-param>
					</xsl:call-template>
					<xsl:text>: </xsl:text>
				</span>
				<xsl:for-each select="//onix:PlaceDefinition[onix:PlaceLabel=current()/onix:RelatedPlace][1]/onix:PlaceName[1]">
					<xsl:value-of select="onix:Name"/>
				</xsl:for-each>
			</div>
		</xsl:for-each>
	</xsl:template>
	<xsl:template name="any-of-resources">
		<xsl:param name="resource-stack"/>
		<xsl:for-each select="//onix:ResourceDefinition[onix:ResourceLabel=current()][1]">
			<xsl:call-template name="resource-details">
				<xsl:with-param name="resource-role" select="'Licensed content'"/>
			</xsl:call-template>
			<xsl:for-each select="onix:ResourceRelatedResource[onix:ResourceResourceRelator='onixPL:IsAnyOf']/onix:RelatedResource">
				<div>
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedResourceCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<xsl:choose>
						<xsl:when test="contains(.,':')">
							<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string">
										<xsl:value-of select="substring-after(.,':')"/>
									</xsl:with-param>
								</xsl:call-template>
							</span>
						</xsl:when>
						<xsl:otherwise>
							<span class="definition-label">
							<xsl:variable name="label-desc" select="translate(//onix:ResourceDefinition[onix:ResourceLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
							<xsl:if test="$label-desc != ''">
								<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
								<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
							</xsl:if>
								<xsl:value-of select="."/>
							</span>
							<xsl:if test="not(contains($resource-stack,concat('//',.,'//')))">
								<div style="margin-left: 48pt">
									<xsl:call-template name="any-of-resources">
										<xsl:with-param name="resource-stack" select="concat($resource-stack,.,'//')"/>
									</xsl:call-template>
								</div>
							</xsl:if>
						</xsl:otherwise>
					</xsl:choose>
				</div>
			</xsl:for-each>
		</xsl:for-each>
	</xsl:template>
	<xsl:template name="resource-details">
		<xsl:param name="resource-role"/>
		<xsl:if test="onix:Description">
			<div class="resource-details">
				<span class="label">Description: </span>
				<xsl:value-of select="onix:Description"/>
			</div>
		</xsl:if>
		<xsl:if test="onix:ResourceIdentifier">
			<div>
				<xsl:value-of select="$resource-role"/>
				<xsl:text> identifier: </xsl:text>
				<xsl:for-each select="onix:ResourceIdentifier[1]">
					<xsl:value-of select="onix:IDValue"/>
					<xsl:text> (</xsl:text>
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='ResourceIDTypeCode']/ople:choice/ople:value[. = current()/onix:ResourceIDType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
						<xsl:call-template name="space-camel-case">
							<xsl:with-param name="in-string" select="substring-after(onix:ResourceIDType,':')"/>
						</xsl:call-template>
					</span>
					<xsl:text>)</xsl:text>
				</xsl:for-each>
				<xsl:for-each select="onix:ResourceIdentifier[position() != 1]">
					<xsl:text> / </xsl:text>
					<xsl:value-of select="onix:IDValue"/>
					<xsl:text> (</xsl:text>
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='ResourceIDTypeCode']/ople:choice/ople:value[. = current()/onix:ResourceIDType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
						<xsl:call-template name="space-camel-case">
							<xsl:with-param name="in-string" select="substring-after(onix:ResourceIDType,':')"/>
						</xsl:call-template>
					</span>
					<xsl:text>)</xsl:text>
				</xsl:for-each>
			</div>
		</xsl:if>
		<xsl:call-template name="notes-and-references"/>
	</xsl:template>
	<xsl:template name="document-details">
		<br/>
		<xsl:if test="onix:Description">
			<xsl:value-of select="onix:Description"/>
		</xsl:if>
		<xsl:for-each select="onix:DocumentIdentifier">
			<div style="margin-left: 48pt;">
				<xsl:choose>
					<xsl:when test="onix:DocumentIDType='onixPL:Proprietary' and onix:IDTypeName">
						<span class="label">
							<xsl:value-of select="onix:IDTypeName"/>
							<xsl:text> (proprietary): </xsl:text>
						</span>
					</xsl:when>
					<xsl:otherwise>
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='DocumentIDTypeCode']/ople:choice/ople:value[. = current()/onix:DocumentIDType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(onix:DocumentIDType,':')"/>
							</xsl:call-template>
							<xsl:text>: </xsl:text>
						</span>
					</xsl:otherwise>
				</xsl:choose>
				<xsl:choose>
					<xsl:when test="(onix:DocumentIDType='onixPL:URI' or onix:DocumentIDType='onixPL:URL') and $valid-uri-schemes//Scheme=substring-before(onix:IDValue,':')">
						<a href="{onix:IDValue}">
							<xsl:value-of select="onix:IDValue"/>
						</a>
					</xsl:when>
					<xsl:otherwise>
						<xsl:value-of select="onix:IDValue"/>
					</xsl:otherwise>
				</xsl:choose>
			</div>
		</xsl:for-each>
		<xsl:call-template name="notes-and-references"/>
	</xsl:template>
	<xsl:template name="notes-and-references">
		<xsl:for-each select="onix:Annotation">
			<div class="license-annotation-text">
				<xsl:choose>
					<xsl:when test="onix:AnnotationType">
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='AnnotationTypeCode']/ople:choice/ople:value[. = current()/onix:AnnotationType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(onix:AnnotationType,':')"/>
							</xsl:call-template>
							<xsl:text>: </xsl:text>
						</span>
					</xsl:when>
					<xsl:otherwise>
						<span class="label">Note: </span>
					</xsl:otherwise>
				</xsl:choose>
				<xsl:value-of select="onix:AnnotationText"/>
				<xsl:if test="onix:Authority">
					<span class="authority">
						<xsl:text> (</xsl:text>
						<xsl:value-of select="onix:Authority"/>
						<xsl:text>)</xsl:text>
					</span>
				</xsl:if>
			</div>
		</xsl:for-each>
		<xsl:for-each select="onix:LicenseDocumentReference">
			<div>
				<xsl:text>See also </xsl:text>
				<a>
					<xsl:if test="onix:SectionIdentifier">
						<xsl:attribute name="href"><xsl:value-of select="onix:SectionIdentifier/onix:IDValue"/></xsl:attribute>
					</xsl:if>
					<xsl:value-of select="onix:DocumentLabel"/>
				</a>
			</div>
		</xsl:for-each>
	</xsl:template>
	<xsl:template name="text-extract">
		<span class="section-number">
			<xsl:value-of select="//onix:LicenseDocumentText[onix:TextElement/@id=current()/@href]/onix:DocumentLabel"/>
		</span>
		<xsl:choose>
			<xsl:when test="//onix:LicenseDocumentText/onix:TextElement[@id=current()/@href]/onix:DisplayNumber">
				<xsl:text> </xsl:text>
				<span class="section-number">
					<xsl:value-of select="//onix:LicenseDocumentText/onix:TextElement[@id=current()/@href]/onix:DisplayNumber"/>
				</span>
				<xsl:text> </xsl:text>
			</xsl:when>
			<xsl:otherwise>: </xsl:otherwise>
		</xsl:choose>
		<xsl:if test="//onix:LicenseDocumentText/onix:TextElement[@id=current()/@href]/onix:TextPreceding">
			<xsl:text>[</xsl:text>
			<xsl:value-of select="//onix:LicenseDocumentText/onix:TextElement[@id=current()/@href]/onix:TextPreceding"/>
			<xsl:text>] </xsl:text>
		</xsl:if>
		<xsl:call-template name="text-with-breaks">
			<xsl:with-param name="text">
				<xsl:value-of select="//onix:LicenseDocumentText/onix:TextElement[@id=current()/@href]/onix:Text"/>
			</xsl:with-param>
		</xsl:call-template>
	</xsl:template>
	<xsl:template name="text-with-breaks">
		<xsl:param name="text"/>
		<xsl:choose>
			<xsl:when test="contains($text,'&#xA;')">
				<xsl:value-of select="substring-before($text,'&#xA;')"/>
				<br/>
				<xsl:call-template name="text-with-breaks">
					<xsl:with-param name="text">
						<xsl:copy-of select="substring-after($text,'&#xA;')"/>
					</xsl:with-param>
				</xsl:call-template>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$text"/>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	<xsl:template name="do-usage">
		<xsl:param name="status"/>
		<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsageTypeCode']/ople:choice/ople:value[. = current()/onix:UsageType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
		<span class="{$status}" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
			<xsl:call-template name="space-camel-case">
				<xsl:with-param name="in-string" select="substring-after(onix:UsageType,':')"/>
			</xsl:call-template>
		</span>
		<xsl:for-each select="onix:UsedResource[1]">
			<xsl:text> </xsl:text>
			<span>
				<xsl:if test="$preposition//Usage[Type=current()/../onix:UsageType]">
					<span class="relation">
						<xsl:value-of select="$preposition//Usage[Type=current()/../onix:UsageType]/Value"/>
					</span>
				</xsl:if>
				<xsl:choose>
					<xsl:when test="contains(.,':')">
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsedResourceCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(.,':')"/>
							</xsl:call-template>
						</span>
					</xsl:when>
					<xsl:otherwise>
						<span class="definition-label">
							<xsl:variable name="label-desc" select="translate(//onix:ResourceDefinition[onix:ResourceLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
							<xsl:if test="$label-desc != ''">
								<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
								<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
							</xsl:if>
							<xsl:value-of select="."/>
						</span>
					</xsl:otherwise>
				</xsl:choose>
			</span>
		</xsl:for-each>
		<xsl:for-each select="onix:UsedResource[position() != 1]">
			<span>
				<span class="relation"> or </span>
				<xsl:choose>
					<xsl:when test="contains(.,':')">
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsedResourceCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(.,':')"/>
							</xsl:call-template>
						</span>
					</xsl:when>
					<xsl:otherwise>
						<span class="definition-label">
							<xsl:variable name="label-desc" select="translate(//onix:ResourceDefinition[onix:ResourceLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
							<xsl:if test="$label-desc != ''">
								<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
								<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
							</xsl:if>
							<xsl:value-of select="."/>
						</span>
					</xsl:otherwise>
				</xsl:choose>
			</span>
		</xsl:for-each>
		<xsl:if test="onix:UsageMethod">
			<span>
				<span class="relation"> by </span>
				<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsageMethodCode']/ople:choice/ople:value[. = current()/onix:UsageMethod[1]]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
				<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
					<xsl:call-template name="space-camel-case">
						<xsl:with-param name="in-string" select="substring-after(onix:UsageMethod[1],':')"/>
					</xsl:call-template>
				</span>
				<xsl:for-each select="onix:UsageMethod[position() != 1]">
					<span class="relation"> or&#xA0;</span>
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsageMethodCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
						<xsl:call-template name="space-camel-case">
							<xsl:with-param name="in-string" select="substring-after(.,':')"/>
						</xsl:call-template>
					</span>
				</xsl:for-each>
			</span>
		</xsl:if>
		<xsl:for-each select="onix:UsageRelatedResource">
			<span>
				<xsl:choose>
					<xsl:when test="onix:UsageResourceRelator='onixPL:TargetResource'">
						<span class="relation"> in </span>
					</xsl:when>
					<xsl:when test="onix:UsageResourceRelator='onixPL:ExcerptMustInclude'">
						<span class="relation"> (Must include: </span>
					</xsl:when>
					<xsl:otherwise>
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsageResourceRelatorCode']/ople:choice/ople:value[. = current()/onix:UsageResourceRelator]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<xsl:text> (</xsl:text>
						<span class="relation code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(onix:UsageResourceRelator,':')"/>
							</xsl:call-template>
							<xsl:text>: </xsl:text>
						</span>
					</xsl:otherwise>
				</xsl:choose>
				<xsl:for-each select="onix:RelatedResource">
					<xsl:if test="preceding-sibling::onix:RelatedResource">
						<span class="relation"> or&#xA0;</span>
					</xsl:if>
					<xsl:choose>
						<xsl:when test="contains(.,':')">
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedResourceCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(.,':')"/>
								</xsl:call-template>
							</span>
						</xsl:when>
						<xsl:otherwise>
							<span class="definition-label">
								<xsl:variable name="label-desc" select="translate(//onix:ResourceDefinition[onix:ResourceLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
								<xsl:if test="$label-desc != ''">
									<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
									<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
								</xsl:if>
								<xsl:value-of select="."/>
							</span>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:for-each>
				<xsl:if test="onix:UsageResourceRelator!='onixPL:TargetResource'">
					<xsl:text>)</xsl:text>
				</xsl:if>
			</span>
		</xsl:for-each>
		<xsl:for-each select="onix:UsageRelatedAgent">
			<span>
				<xsl:text> </xsl:text>
				<xsl:choose>
					<xsl:when test="onix:UsageAgentRelator='onixPL:ReceivingAgent'">
						<span class="relation">to </span>
					</xsl:when>
					<xsl:otherwise>
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsageAgentRelatorCode']/ople:choice/ople:value[. = current()/onix:UsageAgentRelator]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<span class="relation code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(onix:UsageAgentRelator,':')"/>
							</xsl:call-template>
							<xsl:text>: </xsl:text>
						</span>
					</xsl:otherwise>
				</xsl:choose>
				<xsl:for-each select="onix:RelatedAgent">
					<xsl:if test="preceding-sibling::onix:RelatedAgent">
						<span class="relation"> or&#xA0;</span>
					</xsl:if>
					<xsl:choose>
						<xsl:when test="contains(.,':')">
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedAgentCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(.,':')"/>
								</xsl:call-template>
							</span>
						</xsl:when>
						<xsl:otherwise>
							<span class="definition-label">
								<xsl:variable name="label-desc" select="translate(//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
								<xsl:if test="$label-desc != ''">
									<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
									<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
								</xsl:if>
								<xsl:value-of select="."/>
							</span>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:for-each>
			</span>
		</xsl:for-each>
		<xsl:for-each select="onix:UsageRelatedPlace">
			<span>
				<xsl:text> </xsl:text>
				<xsl:choose>
					<xsl:when test="onix:UsagePlaceRelator='onixPL:PlaceOfDeposit'">
						<span class="relation">in </span>
					</xsl:when>
					<xsl:when test="onix:UsagePlaceRelator='onixPL:PlaceOfUsage' and ../onix:UsageType='onixPL:Access'">
						<span class="relation">from </span>
					</xsl:when>
					<xsl:otherwise>
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsagePlaceRelatorCode']/ople:choice/ople:value[. = current()/onix:UsagePlaceRelator]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<span class="relation code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(onix:UsagePlaceRelator,':')"/>
							</xsl:call-template>
							<xsl:text> </xsl:text>
						</span>
					</xsl:otherwise>
				</xsl:choose>
				<xsl:for-each select="onix:RelatedPlace">
					<xsl:if test="preceding-sibling::onix:RelatedPlace">
						<span class="relation"> or&#xA0;</span>
					</xsl:if>
					<xsl:choose>
						<xsl:when test="contains(.,':')">
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedPlaceCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(.,':')"/>
								</xsl:call-template>
							</span>
						</xsl:when>
						<xsl:otherwise>
							<span class="definition-label">
								<xsl:variable name="label-desc" select="translate(//onix:PlaceDefinition[onix:PlaceLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
								<xsl:if test="$label-desc != ''">
									<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
									<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
								</xsl:if>
								<xsl:value-of select="."/>
							</span>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:for-each>
			</span>
		</xsl:for-each>
		<xsl:if test="onix:UsagePurpose">
			<span>
				<span class="relation"> for </span>
				<xsl:for-each select="onix:UsagePurpose">
					<xsl:if test="preceding-sibling::onix:UsagePurpose">
						<span class="relation"> or&#xA0;</span>
					</xsl:if>
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsagePurposeCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
						<xsl:call-template name="space-camel-case">
							<xsl:with-param name="in-string" select="substring-after(.,':')"/>
						</xsl:call-template>
					</span>
				</xsl:for-each>
			</span>
		</xsl:if>
		<xsl:if test="onix:UsageException">
			<span>
				<xsl:text> (</xsl:text>
				<xsl:for-each select="onix:UsageException">
					<xsl:if test="preceding-sibling::onix:UsageException">
						<span class="relation"> or&#xA0;</span>
					</xsl:if>
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsageExceptionTypeCode']/ople:choice/ople:value[. = current()/onix:UsageExceptionType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
						<xsl:call-template name="space-camel-case">
							<xsl:with-param name="in-string" select="substring-after(onix:UsageExceptionType,':')"/>
						</xsl:call-template>
					</span>
				</xsl:for-each>
				<xsl:text>)</xsl:text>
			</span>
		</xsl:if>
		<xsl:if test="onix:UsageQuantity">
			<xsl:text> (</xsl:text>
			<xsl:for-each select="onix:UsageQuantity">
				<xsl:if test="preceding-sibling::onix:UsageQuantity">
					<xsl:text> - </xsl:text>
				</xsl:if>
				<xsl:if test="onix:UsageQuantityType!='onixPL:NumberOfCopiesPermitted'">
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsageQuantityTypeCode']/ople:choice/ople:value[. = current()/onix:UsageQuantityType]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
						<xsl:call-template name="space-camel-case">
							<xsl:with-param name="in-string" select="substring-after(onix:UsageQuantityType,':')"/>
						</xsl:call-template>
						<xsl:text>: </xsl:text>
					</span>
				</xsl:if>
				<xsl:if test="onix:QuantityDetail/onix:Proximity">
					<xsl:choose>
						<xsl:when test="onix:QuantityDetail/onix:Value=1">
							<xsl:choose>
								<xsl:when test="onix:QuantityDetail/onix:Proximity='onixPL:Exactly'">Single </xsl:when>
								<xsl:when test="onix:QuantityDetail/onix:Proximity='onixPL:NotMoreThan'">Single </xsl:when>
								<xsl:otherwise>
									<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='ProximityCode']/ople:choice/ople:value[. = current()/onix:QuantityDetail/onix:Proximity]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
									<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
										<xsl:call-template name="space-camel-case">
											<xsl:with-param name="in-string" select="substring-after(onix:QuantityDetail/onix:Proximity,':')"/>
										</xsl:call-template>
									</span>
									<xsl:text> </xsl:text>
									<xsl:value-of select="onix:QuantityDetail/onix:Value"/>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:when>
						<xsl:otherwise>
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='ProximityCode']/ople:choice/ople:value[. = current()/onix:QuantityDetail/onix:Proximity]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(onix:QuantityDetail/onix:Proximity,':')"/>
								</xsl:call-template>
							</span>
							<xsl:text> </xsl:text>
							<xsl:value-of select="onix:QuantityDetail/onix:Value"/>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:if>
				<xsl:text> </xsl:text>
				<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='QuantityUnitCode']/ople:choice/ople:value[. = current()/onix:QuantityDetail/onix:QuantityUnit]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
				<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
					<xsl:choose>
						<xsl:when test="onix:QuantityDetail/onix:Value=1">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(ople:make-singular(onix:QuantityDetail/onix:QuantityUnit),':')"/>
							</xsl:call-template>
						</xsl:when>
						<xsl:otherwise>
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(onix:QuantityDetail/onix:QuantityUnit,':')"/>
							</xsl:call-template>
						</xsl:otherwise>
					</xsl:choose>
				</span>
			</xsl:for-each>
			<xsl:text>)</xsl:text>
		</xsl:if>
		<xsl:if test="onix:UsageCondition">
			<span>
				<xsl:text> (</xsl:text>
				<span style="font-style: italic;">
					<xsl:text>Condition: </xsl:text>
				</span>
				<xsl:for-each select="onix:UsageCondition">
					<xsl:if test="preceding-sibling::onix:UsageCondition">
						<span class="relation"> and&#xA0;</span>
					</xsl:if>
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='UsageConditionCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
						<xsl:call-template name="space-camel-case">
							<xsl:with-param name="in-string" select="substring-after(.,':')"/>
						</xsl:call-template>
					</span>
				</xsl:for-each>
				<xsl:text>)</xsl:text>
			</span>
		</xsl:if>
		<xsl:call-template name="notes-and-references"/>
	</xsl:template>
	<xsl:template name="do-other-terms">
		<xsl:param name="terms-type"/>
		<xsl:variable name="term-id" select="concat('term-',generate-id())"/>
		<tr valign="top">
			<xsl:if test="onix:LicenseTextLink">
				<xsl:attribute name="class">trigger</xsl:attribute>
				<xsl:attribute name="onclick">showHidden('<xsl:value-of select="$term-id"/>')</xsl:attribute>
			</xsl:if>
			<td>&#xA0;</td>
			<td colspan="2">
				<div class="{$terms-type}">
					<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name=concat($terms-type,'TypeCode')]/ople:choice/ople:value[. = current()/*[name()=concat($terms-type,'Type')]]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
					<span class="other-terms" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
						<xsl:call-template name="space-camel-case">
							<xsl:with-param name="in-string" select="substring-after(*[name()=concat($terms-type,'Type')],':')"/>
						</xsl:call-template>
					</span>
					<xsl:if test="onix:TermStatus">
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name=concat($terms-type,'TermStatusCode')]/ople:choice/ople:value[. = current()/onix:TermStatus]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<br/>
						<span class="label">Status: </span>
						<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(onix:TermStatus,':')"/>
							</xsl:call-template>
						</span>
					</xsl:if>
					<xsl:for-each select="*[name()=concat($terms-type,'Quantity')]">
						<br/>
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name=concat($terms-type,'QuantityTypeCode')]/ople:choice/ople:value[. = current()/*[name()=concat($terms-type,'QuantityType')]]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<span class="label code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:call-template name="space-camel-case">
								<xsl:with-param name="in-string" select="substring-after(*[name()=concat($terms-type,'QuantityType')],':')"/>
							</xsl:call-template>
							<xsl:text>: </xsl:text>
						</span>
						<xsl:if test="onix:QuantityDetail/onix:Proximity">
							<xsl:choose>
								<xsl:when test="onix:QuantityDetail/onix:Value=1">
									<xsl:choose>
										<xsl:when test="onix:QuantityDetail/onix:Proximity='onixPL:Exactly'">Single </xsl:when>
										<xsl:when test="onix:QuantityDetail/onix:Proximity='onixPL:NotMoreThan'">Single </xsl:when>
										<xsl:otherwise>
											<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='ProximityCode']/ople:choice/ople:value[. = current()/onix:QuantityDetail/onix:Proximity]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
											<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
												<xsl:call-template name="space-camel-case">
													<xsl:with-param name="in-string" select="substring-after(onix:QuantityDetail/onix:Proximity,':')"/>
												</xsl:call-template>
											</span>
											<xsl:text> </xsl:text>
											<xsl:value-of select="onix:QuantityDetail/onix:Value"/>
										</xsl:otherwise>
									</xsl:choose>
								</xsl:when>
								<xsl:otherwise>
									<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='ProximityCode']/ople:choice/ople:value[. = current()/onix:QuantityDetail/onix:Proximity]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
									<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
										<xsl:call-template name="space-camel-case">
											<xsl:with-param name="in-string" select="substring-after(onix:QuantityDetail/onix:Proximity,':')"/>
										</xsl:call-template>
									</span>
									<xsl:text> </xsl:text>
									<xsl:value-of select="onix:QuantityDetail/onix:Value"/>
								</xsl:otherwise>
							</xsl:choose>
						</xsl:if>
						<xsl:text> </xsl:text>
						<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='QuantityUnitCode']/ople:choice/ople:value[. = current()/onix:QuantityDetail/onix:Proximity]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
						<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
							<xsl:choose>
								<xsl:when test="onix:QuantityDetail/onix:Value=1">
									<xsl:call-template name="space-camel-case">
										<xsl:with-param name="in-string" select="substring-after(ople:make-singular(onix:QuantityDetail/onix:QuantityUnit),':')"/>
									</xsl:call-template>
								</xsl:when>
								<xsl:otherwise>
									<xsl:call-template name="space-camel-case">
										<xsl:with-param name="in-string" select="substring-after(onix:QuantityDetail/onix:QuantityUnit,':')"/>
									</xsl:call-template>
								</xsl:otherwise>
							</xsl:choose>
						</span>
					</xsl:for-each>
					<xsl:for-each select="*[name()=concat($terms-type,'RelatedAgent')]">
						<span>
							<br/>
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name=concat($terms-type,'AgentRelatorCode')]/ople:choice/ople:value[. = current()/*[name()=concat($terms-type,'AgentRelator')]]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="relation code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(*[name()=concat($terms-type,'AgentRelator')],':')"/>
								</xsl:call-template>
								<xsl:text>: </xsl:text>
							</span>
							<xsl:for-each select="onix:RelatedAgent">
								<xsl:if test="preceding-sibling::onix:RelatedAgent">
									<span class="relation"> or&#xA0;</span>
								</xsl:if>
								<xsl:choose>
									<xsl:when test="contains(.,':')">
										<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedAgentCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
										<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
											<xsl:call-template name="space-camel-case">
												<xsl:with-param name="in-string" select="substring-after(.,':')"/>
											</xsl:call-template>
										</span>
									</xsl:when>
									<xsl:otherwise>
										<span class="definition-label">
											<xsl:variable name="label-desc" select="translate(//onix:AgentDefinition[onix:AgentLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
											<xsl:if test="$label-desc != ''">
												<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
												<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
											</xsl:if>
											<xsl:value-of select="."/>
										</span>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:for-each>
						</span>
					</xsl:for-each>
					<xsl:for-each select="*[name()=concat($terms-type,'RelatedResource')]">
						<span>
							<br/>
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name=concat($terms-type,'ResourceRelatorCode')]/ople:choice/ople:value[. = current()/*[name()=concat($terms-type,'ResourceRelator')]]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="relation code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(*[name()=concat($terms-type,'ResourceRelator')],':')"/>
								</xsl:call-template>
								<xsl:text>: </xsl:text>
							</span>
							<xsl:for-each select="onix:RelatedResource">
								<xsl:if test="preceding-sibling::onix:RelatedResource">
									<span class="relation"> or&#xA0;</span>
								</xsl:if>
								<xsl:choose>
									<xsl:when test="contains(.,':')">
										<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedResourceCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
										<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
											<xsl:call-template name="space-camel-case">
												<xsl:with-param name="in-string" select="substring-after(.,':')"/>
											</xsl:call-template>
										</span>
									</xsl:when>
									<xsl:otherwise>
										<span class="definition-label">
											<xsl:variable name="label-desc" select="translate(//onix:ResourceDefinition[onix:ResourceLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
											<xsl:if test="$label-desc != ''">
												<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
												<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
											</xsl:if>
											<xsl:value-of select="."/>
										</span>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:for-each>
						</span>
					</xsl:for-each>
					<xsl:for-each select="*[name()=concat($terms-type,'RelatedTimePoint')]">
						<span>
							<br/>
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name=concat($terms-type,'TimePointRelatorCode')]/ople:choice/ople:value[. = current()/*[name()=concat($terms-type,'TimePointRelator')]]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="relation code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(*[name()=concat($terms-type,'TimePointRelator')],':')"/>
								</xsl:call-template>
								<xsl:text>: </xsl:text>
							</span>
							<xsl:for-each select="onix:RelatedTimePoint">
								<xsl:if test="preceding-sibling::onix:RelatedTimePoint">
									<span class="relation"> or&#xA0;</span>
								</xsl:if>
								<xsl:choose>
									<xsl:when test="contains(.,':')">
										<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedTimePointCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
										<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
											<xsl:call-template name="space-camel-case">
												<xsl:with-param name="in-string" select="substring-after(.,':')"/>
											</xsl:call-template>
										</span>
									</xsl:when>
									<xsl:otherwise>
										<xsl:choose>
											<xsl:when test="//onix:TimePointDefinition[onix:TimePointLabel=current()][1]/onix:TimePointIdentifier">
												<span style="color: black;">
													<xsl:value-of select="//onix:TimePointDefinition[onix:TimePointLabel=current()][1]/onix:TimePointIdentifier[1]/onix:IDValue"/>
												</span>
											</xsl:when>
											<xsl:otherwise>
												<span class="definition-label">
													<xsl:variable name="label-desc" select="translate(//onix:TimePointDefinition[onix:TimePointLabel=current()][1]/onix:Description,$apos,'&#x2019;')"/>
													<xsl:if test="$label-desc != ''">
														<xsl:attribute name="onmouseover"><xsl:value-of select="concat('Tip(',$apos,$label-desc,$apos,')')"/></xsl:attribute>
														<xsl:attribute name="onmouseout">UnTip()</xsl:attribute>
													</xsl:if>
													<xsl:value-of select="."/>
												</span>
											</xsl:otherwise>
										</xsl:choose>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:for-each>
						</span>
					</xsl:for-each>
					<xsl:for-each select="*[name()=concat($terms-type,'RelatedPlace')]">
						<span>
							<br/>
							<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name=concat($terms-type,'PlaceRelatorCode')]/ople:choice/ople:value[. = current()/*[name()=concat($terms-type,'PlaceRelator')]]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
							<span class="relation code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
								<xsl:call-template name="space-camel-case">
									<xsl:with-param name="in-string" select="substring-after(*[name()=concat($terms-type,'PlaceRelator')],':')"/>
								</xsl:call-template>
								<xsl:text>: </xsl:text>
							</span>
							<xsl:for-each select="onix:RelatedPlace">
								<xsl:if test="preceding-sibling::onix:RelatedPlace">
									<span class="relation"> or&#xA0;</span>
								</xsl:if>
								<xsl:choose>
									<xsl:when test="contains(.,':')">
										<xsl:variable name="code-value-desc" select="translate($code-lists//ople:def[@name='RelatedPlaceCode']/ople:choice/ople:value[. = current()]/following-sibling::ople:documentation[1]/ople:note,$apos,'&#x2019;')"/>
										<span class="code-value" onmouseover="{concat('Tip(',$apos,$code-value-desc,$apos,')')}" onmouseout="UnTip()">
											<xsl:call-template name="space-camel-case">
												<xsl:with-param name="in-string" select="substring-after(.,':')"/>
											</xsl:call-template>
										</span>
									</xsl:when>
									<xsl:otherwise>
										<span>
											<xsl:choose>
												<xsl:when test="//onix:PlaceDefinition[onix:PlaceLabel=current()][1]/onix:PlaceName">
													<xsl:attribute name="style">color: black;</xsl:attribute>
													<xsl:value-of select="//onix:PlaceDefinition[onix:PlaceLabel=current()][1]/onix:PlaceName[1]/onix:Name"/>
												</xsl:when>
												<xsl:otherwise>
													<xsl:attribute name="style">color: blue;</xsl:attribute>
													<xsl:value-of select="."/>
												</xsl:otherwise>
											</xsl:choose>
											<xsl:if test="//onix:PlaceDefinition[onix:PlaceLabel=current()][1]/onix:PlaceIdentifier">
												<span style="color: black;">
													<xsl:text> (</xsl:text>
														<xsl:value-of select="//onix:PlaceDefinition[onix:PlaceLabel=current()][1]/onix:PlaceIdentifier[1]/onix:IDValue"/>
													<xsl:text>)</xsl:text>
												</span>
											</xsl:if>
										</span>
									</xsl:otherwise>
								</xsl:choose>
							</xsl:for-each>
						</span>
					</xsl:for-each>
				</div>
				<xsl:call-template name="notes-and-references"/>
			</td>
			<td>
				<xsl:if test="onix:LicenseTextLink">
					<div class="more-info"><img alt="More information" src="{$info.png}"/></div>
				</xsl:if>
			</td>
			<td>
				<div class="hidden" id="{$term-id}">
					<xsl:for-each select="onix:LicenseTextLink">
						<div class="license-extract">
							<xsl:call-template name="text-extract"/>
						</div>
					</xsl:for-each>
				</div>
			</td>
		</tr>
	</xsl:template>
	<xsl:template match="@* | node()"/>
	<xsl:template name="space-camel-case">
		<xsl:param name="in-string"/>
		<xsl:param name="lastchar"/>
		<xsl:param name="first" select="'yes'"/>
		<xsl:variable name="thischar" select="substring($in-string,1,1)"/>
		<xsl:variable name="nextchar" select="substring($in-string,2,1)"/>
		<xsl:if test="string-length($in-string) &gt; 0">
			<xsl:variable name="outstr">
				<xsl:if test="contains('ABCDEFGHIJKLMNOPQRSTUVWXYZ', $thischar) and $lastchar and (contains('abcdefghijklmnopqrstuvwxyz',$lastchar) or ($nextchar and contains('abcdefghijklmnopqrstuvwxyz',$nextchar)) and not(contains(' -_',$lastchar)))">
					<xsl:text> </xsl:text>
				</xsl:if>
				<xsl:value-of select="$thischar"/>
			</xsl:variable>
			<xsl:value-of select="$outstr"/>
			<xsl:call-template name="space-camel-case">
				<xsl:with-param name="in-string" select="substring($in-string,2)"/>
				<xsl:with-param name="lastchar" select="$thischar"/>
				<xsl:with-param name="first" select="'no'"/>
			</xsl:call-template>
		</xsl:if>
	</xsl:template>
	<xsl:function name="ople:make-singular">
		<xsl:param name="unit"/>
		<xsl:choose>
			<xsl:when test="substring($unit,string-length($unit)-2,3)='ies'">
				<xsl:value-of select="concat(substring($unit,1,string-length($unit)-3),'y')"/>
			</xsl:when>
			<xsl:when test="substring($unit,string-length($unit),1)='s'">
				<xsl:value-of select="substring($unit,1,string-length($unit)-1)"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="$unit"/>
			</xsl:otherwise>
		</xsl:choose>
		
	</xsl:function>
</xsl:stylesheet>
