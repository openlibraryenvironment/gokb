package com.k_int.gokb.refine.commands;
import com.google.refine.commands.Command;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import com.k_int.gokb.refine.notifications.NotificationStack;
import com.k_int.gokb.refine.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearWarning extends Command{
    final static Logger logger = LoggerFactory.getLogger("GOKb-clear-warning_command");

	@Override
  	public void doPost(HttpServletRequest request, HttpServletResponse response)throws ServletException, IOException {
	    String validation_message = request.getParameter("validation_message");
    	String json_mock = "{'text':'"+validation_message+"','col':'unknown','sub_type':'unknown','type':'notice'}";
    	hide_message(json_mock);
  	}

  	private void hide_message(String json_mock){
  		NotificationStack stack = NotificationStack.get("validation_hidden");
        ValidationMessage n= ValidationMessage.fromJSON(json_mock, ValidationMessage.class);
        stack.add(n);

  	}

}