package com.k_int.refine.es_recon.commands;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.commands.Command;
import com.k_int.gokb.module.GOKbModuleImpl;


public class GetTypes extends Command {
  final static Logger logger = LoggerFactory.getLogger("ESRecon-get-types");

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    try {
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      
      // The writer.
      JSONWriter writer = new JSONWriter(response.getWriter());
      
      // Get the list of workspaces.
      String[] types = GOKbModuleImpl.singleton.getESTypes();
      // Open an object.
      writer.object();
      writer.key("types");
      
        // Open the array.
        writer.array();
        
        // Write each value.
        for (String type : types) {
          writer.value(type);
        }
        
        // Close the array.
        writer.endArray();
      writer.endObject();
      
    } catch (JSONException e) {
      respondException(response, e);
    }
  }
}