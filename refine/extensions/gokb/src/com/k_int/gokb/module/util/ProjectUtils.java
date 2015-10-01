package com.k_int.gokb.module.util;

import java.util.List;

import com.google.refine.model.Project;

public class ProjectUtils {

  public static String caseInsensitiveColumnName (Project project, String columnName) {
    
    List<String> cols = project.columnModel.getColumnNames();
    String actualName = null;
    for (int i=0; i<cols.size() && (actualName == null); i++) {
        
      // Get the current name
      String col = cols.get(i);
      
      // Get each column name and check for case-insensitive match.
      if (columnName.equalsIgnoreCase(col)) {

        // Set the actual name
        actualName = col;
      }
    }
    
    return actualName;
  }
}