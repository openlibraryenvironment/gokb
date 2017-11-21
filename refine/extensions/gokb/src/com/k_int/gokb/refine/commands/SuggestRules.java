package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;
import com.k_int.gokb.refine.RefineUtil;

import com.google.refine.ProjectManager;
import com.google.refine.model.Project;


public class SuggestRules extends A_RefineAPIBridge {
  final static Logger logger = LoggerFactory.getLogger("GOKb-suggest-rules_command");

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

  private void handleRequest (HttpServletRequest request, final HttpServletResponse response) {
    // Get the project manager and flag that it is busy.
    final ProjectManager pm = ProjectManager.singleton;
    pm.setBusy(true);
    try {
      // Get the project.
      final Project project = getProject(request);

      // Get files sent to this method.
      Map<String, Object> files = files(request);

      // Ensure the project has been saved.
      pm.ensureProjectSaved(project.id);

      // Add the project data zip to the files list.
      files.put("dataZip", RefineUtil.projectToDataZip(project));

      // Now we need to pass the data to the API.
      postToAPI(response, "suggestRulesFromData", params(request), files, new RefineAPICallback(){

        @Override
        protected void onSuccess(InputStream result, int responseCode)
            throws Exception {

          // Proxy through the api response to the client.
          proxyReturn (response, result);
        }
      });

    } catch (Exception e) {

      // Respond with the error page.
      respondOnError(request, response, e);

    } finally {
      // Make sure we clear the busy flag.
      pm.setBusy(false);
    }
  }
}