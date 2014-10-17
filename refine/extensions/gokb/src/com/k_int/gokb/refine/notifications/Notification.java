package com.k_int.gokb.refine.notifications;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.k_int.gokb.refine.Jsonizable;

import com.google.gson.Gson;

public class Notification extends Jsonizable {
  private String text;
  private String title;
  private boolean hide;
  private String id;
  private Map<String,Object> buttons = new HashMap<String,Object>();
  
  public String getId() {
    return id;
  }
  
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
  
  @SuppressWarnings("unchecked")
  public static <T extends Notification> T fromJSON (String json) {
    Gson gson = new Gson();
    return (T) gson.fromJson(json, Notification.class);
  }

  @Override
  public void write (JSONWriter writer, Properties options)
      throws JSONException {
    
    // JSON value.
    JSONObject me = new JSONObject(toJSON());
    
    // Write the object.
    writer.value(me);
  }

  public Map<String,Object> getButtons () {
    return buttons;
  }
}
