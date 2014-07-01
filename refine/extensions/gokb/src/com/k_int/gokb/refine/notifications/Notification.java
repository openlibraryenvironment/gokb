package com.k_int.gokb.refine.notifications;

import com.google.gson.Gson;

public class Notification {
  private String text;
  private String title;
  private boolean hide;
  
  public String getText () {
    return text;
  }
  
  public void setText (String text) {
    this.text = text;
  }
  
  public String getTitle () {
    return title;
  }
  
  public void setTitle (String title) {
    this.title = title;
  }

  public boolean isHide () {
    return hide;
  }

  public void setHide (boolean hide) {
    this.hide = hide;
  }
  
  public static <T extends Notification> T fromJSON (String json, Class<T> theClass) {
    Gson gson = new Gson();
    return gson.fromJson(json, theClass);
  }
  
  public String toJSON () {
    Gson gson = new Gson();
    return gson.toJson(this);
  }
}
