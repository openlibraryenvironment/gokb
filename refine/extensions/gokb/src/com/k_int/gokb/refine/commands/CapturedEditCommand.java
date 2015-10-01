package com.k_int.gokb.refine.commands;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;

import com.google.refine.commands.cell.TextTransformCommand;
import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Project;
import com.k_int.gokb.module.RequestParser;
import com.k_int.gokb.refine.operations.CapturedEditOperation;

public class CapturedEditCommand extends TextTransformCommand {
  @Override
  protected AbstractOperation createOperation(Project project,
      HttpServletRequest request, JSONObject engineConfig) throws Exception {
    
    String columnName = request.getParameter("columnName");
    String expression = request.getParameter("expression");
    String onError = request.getParameter("onError");
    boolean repeat = "true".equals(request.getParameter("repeat"));
    
    String newVal = request.getParameter("value");

    int repeatCount = 10;
    String repeatCountString = request.getParameter("repeatCount");
    try {
      repeatCount = Math.max(Math.min(Integer.parseInt(repeatCountString), 10), 0);
    } catch (Exception e) {
    }
    
    final Map<String, String[]> params = RequestParser.parse(request).getParams();
    String[] basedOnCols = params.get("basedOn[]");

    return new CapturedEditOperation(
      engineConfig, 
      columnName, 
      expression, 
      CapturedEditOperation.stringToOnError(onError),
      repeat,
      repeatCount,
      basedOnCols,
      newVal
    );
  }
}