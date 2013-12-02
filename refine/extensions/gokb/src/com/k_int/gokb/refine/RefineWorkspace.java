package com.k_int.gokb.refine;

import java.io.File;

public class RefineWorkspace {
  private String name;
  private String URL;
  private File wsFolder;
  public RefineWorkspace (String name, String URL, File wsFolder) {
    this.name = name;
    this.URL = URL;
    this.wsFolder = wsFolder;
  }
  public String getName () {
    return name;
  }
  public String getURL () {
    return URL;
  }
  public File getWsFolder () {
    return wsFolder;
  }
}
