package com.k_int.gokb.module;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.log.Log;

public class ExtendedTemplateEngine extends VelocityEngine {

  private VelocityEngine ve;

  public ExtendedTemplateEngine (VelocityEngine ve) throws Exception {
    this.ve = ve;
  }

  @Override
  public boolean mergeTemplate(String templateName, String encoding, Context context, Writer writer)
      throws ResourceNotFoundException, ParseErrorException, MethodInvocationException, Exception {
    // Before we pass the context object to the template let's append uris with the GOKb module version.
    // This should prevent the need to clear caches after upgrades.
    addModuleVersionToResources(context);
    return ve.mergeTemplate(templateName, encoding, context, writer);
  }

  @Override
  public void init () throws Exception {
    ve.init();
  }

  @Override
  public void init (String propsFilename) throws Exception {
    ve.init(propsFilename);
  }

  @Override
  public void init (Properties p) throws Exception {
    ve.init(p);
  }

  @Override
  public void setProperty (String key, Object value) {
    ve.setProperty(key, value);
  }

  @Override
  public void addProperty (String key, Object value) {
    ve.addProperty(key, value);
  }

  @Override
  public void clearProperty (String key) {
    ve.clearProperty(key);
  }

  @Override
  public void setExtendedProperties (ExtendedProperties configuration) {
    ve.setExtendedProperties(configuration);
  }

  @Override
  public Object getProperty (String key) {
    return ve.getProperty(key);
  }

  @Override
  public boolean evaluate (Context context, Writer out, String logTag,
      String instring) throws ParseErrorException, MethodInvocationException,
      ResourceNotFoundException, IOException {
    return ve.evaluate(context, out, logTag, instring);
  }

  @Override
  public boolean evaluate (Context context, Writer writer, String logTag,
      Reader reader) throws ParseErrorException, MethodInvocationException,
      ResourceNotFoundException, IOException {
    return ve.evaluate(context, writer, logTag, reader);
  }

  @Override
  public boolean invokeVelocimacro (String vmName, String logTag,
      String[] params, Context context, Writer writer) throws Exception {
    return ve.invokeVelocimacro(vmName, logTag, params, context, writer);
  }

  @Override
  public boolean mergeTemplate (String templateName, Context context,
      Writer writer) throws ResourceNotFoundException, ParseErrorException,
      MethodInvocationException, Exception {
    return ve.mergeTemplate(templateName, context, writer);
  }

  @Override
  public Template getTemplate (String name) throws ResourceNotFoundException,
  ParseErrorException, Exception {
    return ve.getTemplate(name);
  }

  @Override
  public Template getTemplate (String name, String encoding)
      throws ResourceNotFoundException, ParseErrorException, Exception {
    return ve.getTemplate(name, encoding);
  }

  @Override
  public boolean resourceExists (String resourceName) {
    return ve.resourceExists(resourceName);
  }

  @Override
  public Log getLog () {
    return ve.getLog();
  }

  @Override
  public void setApplicationAttribute (Object key, Object value) {
    ve.setApplicationAttribute(key, value);
  }

  @Override
  public Object getApplicationAttribute (Object key) {
    return ve.getApplicationAttribute(key);
  }

  private static final String REPLACEMENT_REGEX = "(\\<(script|link).*(src|href)\\s*\\=\\s*[\\\"\\'][^\\\"\\']+)([\\\"\\'][^\\>]*\\>)";

  private static void addModuleVersionToResources (Context context) {
    
    // Grab the module version.
    String version = GOKbModuleImpl.getVersion();
    
    if (!"development".equals(version)) {
    
      // Go through each option and append the <link> and <script> locations with the module version. 
      for (Object k : context.getKeys()) {
        String key = (String)k;
  
        // Get the value.
        Object value = context.get(key);
        if (value instanceof String) {
          
          // Replace the text and write the value back.
          String val = (String)value;
          val = val.replaceAll(REPLACEMENT_REGEX, "$1?gokb=" + version + "$4");
          context.put(key, val);
        }
      }
    }
  }
}
