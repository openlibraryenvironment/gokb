package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.ProjectManager;
import com.google.refine.model.Project;

import com.k_int.gokb.module.util.URLConenectionUtils;
import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;
import com.k_int.gokb.refine.RefineUtil;
import com.k_int.gokb.refine.ValidationMessage;
import com.k_int.gokb.refine.notifications.NotificationStack;
import com.k_int.gokb.refine.notifications.Notification;

public class ValidateData extends A_RefineAPIBridge {
  final static Logger logger = LoggerFactory.getLogger("GOKb-validate-data_command");

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
      postToAPI(response, "projectDataValid", params(request), files, new RefineAPICallback(){

        @Override
        protected void onSuccess(InputStream result, int responseCode) throws Exception {
          
            // Add messages to the correct stack.
            NotificationStack stack = NotificationStack.get("validation");
            NotificationStack hidden_stack = NotificationStack.get("validation_hidden");
            // Clear the data as validation messages are always preserved.
            stack.clear();
            
            // The data.
            JSONObject data = URLConenectionUtils.getJSONObjectFromStream(result);
            
            // Grab the messages.
            JSONArray messages = data
              .getJSONObject("result")
              .getJSONArray("messages")
            ;
            System.out.println("Hidden stack: " + hidden_stack.size());

            // Go through each message and try and push a notification for each message.
            for (int i=0; i<messages.length(); i++) {

              ValidationMessage n = ValidationMessage.fromJSON(messages.getJSONObject(i).toString(), ValidationMessage.class);
              boolean isHidden = false;
              for( Notification hidden_msg : hidden_stack){
                if (hidden_msg.getText().equals(n.getText())){
                  System.out.println("MATCH "+hidden_msg.getText());
                  isHidden = true;
                  break;
                }
              }
              // We need to make sure it doesn't automatically hide itself.
              n.setHide(isHidden);
              if(!isHidden){        
                // Add to the stack.
                System.out.println("Add to stack with hidden: "+isHidden);
                stack.add(n);              
              }
            }
            
            // Proxy through the api response to the client.
            respond (response, data.toString());
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
