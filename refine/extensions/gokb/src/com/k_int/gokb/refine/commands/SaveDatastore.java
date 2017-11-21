package com.k_int.gokb.refine.commands;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;

import com.google.refine.ProjectManager;
import com.google.refine.model.Project;


public class SaveDatastore extends A_RefineAPIBridge {
  final static Logger logger = LoggerFactory.getLogger("GOKb-save-datastore_command");

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response);
  }

  private void handleRequest (HttpServletRequest request, HttpServletResponse response) {
    // Get the project manager and flag that it is busy.

    final ProjectManager pm = ProjectManager.singleton;
    pm.setBusy(true);
    try {

      // Get the project.
      final Project project = getProject(request);
      String[] dataStore = params(request).get("ds");
      if (dataStore != null && dataStore.length == 1 ) {
        // Set the metadata.
        project.getMetadata().setCustomMetadata(
            "gokb-data", dataStore[0]
            );

        // Ensure project & Metadata is saved.
        respond(response, "{ \"code\" : \"success\"}");
      }

    } catch (Exception e) {

      // Respond with the error page.
      respondOnError(request, response, e);

    } finally {
      // Make sure we clear the busy flag.
      pm.setBusy(false);
    }
  }
}