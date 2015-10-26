package com.k_int.gokb.refine;

import java.util.List;

import com.k_int.gokb.refine.notifications.Notification;

public class ValidationMessage extends Notification {
  private String col;
  private String facetName;
  private String facetValue;
  private String severity;
  private String sub_type;
  private String transformation;
  private List<String> transformations;
  private String type;
  public String getCol () {
    return col;
  }
  public String getFacetName(){
    return facetName;
  }

  public String getFacetValue(){
    return facetValue;
  }
  public String getSeverity () {
    return severity;
  }
  public String getSub_type(){
    return sub_type;
  }
  public String getTitle () {
    return getCol();
  }
  public String getTransformation(){
    return transformation;
  }
  public List<String> getTransformations () {
    return transformations;
  }
  public String getType () {
    return type;
  }
  public void setCol (String col) {
    this.col = col;
  }
  public void setFacetName(String val){
    this.facetName = val;
  }

  public void setFacetValue(String val){
    this.facetValue = val;
  }

  public void setSeverity (String severity) {
    this.severity = severity;
  }
  public void setSub_type(String stype) {
    this.sub_type = stype;
  }
  public void setTitle (String title) {
    setCol(type);
  }
  public void setTransformation(String val){
    this.transformation = val;
  }
  public void setTransformations (List<String> transformations) {
    this.transformations = transformations;
  }
  public void setType (String type) {
    this.type = type;
  }
}

