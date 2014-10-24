package com.k_int.gokb.module;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectManager;
import com.google.refine.ProjectMetadata;
import com.google.refine.importing.DefaultImportingController;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingManager;
import com.google.refine.importing.ImportingUtilities;
import com.google.refine.util.JSONUtilities;
import com.google.refine.util.ParsingUtilities;
import com.k_int.gokb.refine.RefineUtil;


public class GOKbImportingController extends DefaultImportingController {

  final static Logger logger = LoggerFactory.getLogger("GOKb-importing-controller");

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    // Do the default action first.
    super.doPost(request, response);

    /*
     * The uploaded file is in the POST body as a "file part". If
     * we call request.getParameter() then the POST body will get
     * read and we won't have a chance to parse the body ourselves.
     * This is why we have to parse the URL for parameters ourselves.
     */        
    Properties parameters = ParsingUtilities.parseUrlParameters(request);
    String subCommand = parameters.getProperty("subCommand");

    // The Job ID
    final long jobID = Long.parseLong(parameters.getProperty("jobID"));

    if ("load-raw-data".equals(subCommand)) {

      // MD5 hash in another thread.
      new Thread() {

        @Override
        public void run(){
          try {
            ImportingJob job = ImportingManager.getJob(jobID);
            JSONObject conf = job.getOrCreateDefaultConfig();

            // Examine the job and wait for the project to be updated.
            String state = (String) conf.get("state");
            while (job.updating) {
              // Just keep refreshing the job.
              job = ImportingManager.getJob(jobID);
            }

            // If the only one file has been uploaded then let's MD5 it.
            JSONObject rec = conf.getJSONObject("retrievalRecord");
            if ("ready".equals(state) && rec.getInt("uploadCount") == 1) {

              // We have only 1 file uploaded.
              JSONObject file = (JSONObject)rec.getJSONArray("files").get(0);

              logger.debug(file.getString("location"));

              if (file.get("location") != null) {

                // This is the builder that will hold the base 64 representation of our file. 
                StringBuilder base64 = new StringBuilder();

                String MD5 = RefineUtil.byteArrayToHex(
                  RefineUtil.stringifyAndHashFile (ImportingUtilities.getFile(job, file.getString("location")), base64)
                );

                logger.debug(MD5);

                // Add to the Job Config so it does not get deleted.
                JSONUtilities.safePut(conf, "hash", MD5);
                JSONUtilities.safePut(conf, "file", base64.toString());
              }
            }
          } catch (Exception e) {
            logger.error("Error while trying to create file MD5 hash.", e);
          }
        }
      }.start();

    } else if ("create-project".equals(subCommand)) {

      new Thread() {

        @Override
        public void run() {

          // Wait for the project to finish updating.
          try {
            ImportingJob job = ImportingManager.getJob(jobID);

            // Wait for the project to be created and then set our MD5 metadata attribute.
            while (job.updating) {
              // Just keep refreshing the job.
              job = ImportingManager.getJob(jobID);
            }

            JSONObject config = job.getOrCreateDefaultConfig();

            // Add the hash to the project metadata.
            Long pid = config.getLong("projectID");
            logger.debug("Project ID: " + pid);

            if (pid != null) {

              // Load the metadata and add the hash.
              ProjectMetadata md = ProjectManager.singleton.getProjectMetadata(pid);
              
              if (config.has("hash")) {
                // Add the hash.
                md.setCustomMetadata("hash", config.getString("hash"));
              }
              
              if (config.has("file")) {
                // Add the file.
                md.setCustomMetadata("source-file", config.getString("file"));
              }
            }
          } catch (Exception e) {
            logger.error("Error while trying to set the MD5 hash.", e);
          }
        }
      }.start();
    }
  }
}