package com.k_int.gokb.refine;

import java.io.File;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.Jsonizable;

import com.k_int.gokb.module.GOKbService;

public class RefineWorkspace implements Jsonizable {
  
  private String name;
  private GOKbService service;
  private File wsFolder;
  private boolean available = true;

  final static Logger _logger = LoggerFactory.getLogger("GOKb-RefineWorkspace");
  
  public RefineWorkspace (String name, String URL, File wsFolder) {
    this.name = name;
    this.wsFolder = wsFolder;
    try {
      this.service = new GOKbService (URL, wsFolder);
    } catch (Exception e) {
      _logger.error("Error creating service for " + URL, e);
      this.available = false;
    }
  }
  
  public String getName () {
    return name;
  }
  
  public GOKbService getService () {
    return service;
  }
  
  public File getWsFolder () {
    return wsFolder;
  }
  
  public boolean isAvailable () {
    return available && service.isAlive();
  }

  @Override
  public void write (JSONWriter writer, Properties options) throws JSONException {
    
    // Add Any properties we wish to output when represented in JSON.
    writer.object();
    writer.key("name"); writer.value(getName());
    writer.key("available"); writer.value(isAvailable());
    writer.key("capabilities"); writer.value(getService().getCapabilities().toString());
    writer.endObject();
  }
}