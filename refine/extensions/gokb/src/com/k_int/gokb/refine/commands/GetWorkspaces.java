package com.k_int.gokb.refine.commands;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.commands.Command;
import com.k_int.gokb.refine.GOKbModuleImpl;
import com.k_int.gokb.refine.RefineWorkspace;


public class GetWorkspaces extends Command {
  final static Logger logger = LoggerFactory.getLogger("GOKb-get-workspaces_command");

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    try {
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      
      // The writer.
      JSONWriter writer = new JSONWriter(response.getWriter());
      
      // Get the list of workspaces.
      RefineWorkspace[] wspaces = GOKbModuleImpl.singleton.getWorkspaces();
      
   // Open an object.
      writer.object();
      writer.key("workspaces");
      
      // Open the array.
      writer.array();
      
      // Write each workspace as an object.
      for (RefineWorkspace ws : wspaces) {
        ws.write(writer, null);
      }
      
      // Close the array.
      writer.endArray();
      
      // Add the selected id.
      writer.key("current"); writer.value(GOKbModuleImpl.singleton.getCurrentWorkspaceId());
      writer.endObject();
      
    } catch (JSONException e) {
      respondException(response, e);
    }
  }
}