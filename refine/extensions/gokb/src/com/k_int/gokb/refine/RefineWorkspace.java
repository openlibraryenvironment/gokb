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

  final static Logger _logger = LoggerFactory.getLogger("GOKb-RefineWorkspace");
  private boolean available = true;
  private String baseUrl;
  private String name;
  private GOKbService service;

  private File wsFolder;

  public RefineWorkspace (String name, String URL, File wsFolder) {
    this.name = name;
    this.wsFolder = wsFolder;
    this.baseUrl = URL;
    try {
      this.service = new GOKbService (baseUrl + "api/", wsFolder);
    } catch (Exception e) {
      _logger.error("Error creating service for " + URL, e);
      this.available = false;
    }
  }

  public String getBaseUrl () {
    return baseUrl;
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

  public void setBaseUrl (String baseUrl) {
    this.baseUrl = baseUrl;
  }

  @Override
  public void write (JSONWriter writer, Properties options) throws JSONException {

    // Add Any properties we wish to output when represented in JSON.
    writer.object()
      .key("name").value(getName())
      .key("available").value(isAvailable())
      .key("base_url").value(getBaseUrl())
      .key("service");service.write(writer, options);
    writer.endObject();
  }
}