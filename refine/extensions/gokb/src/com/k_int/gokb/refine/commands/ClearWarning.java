package com.k_int.gokb.refine.commands;
import com.google.refine.commands.Command;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import com.k_int.gokb.refine.notifications.NotificationStack;
import com.k_int.gokb.refine.notifications.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearWarning extends Command{
    final static Logger logger = LoggerFactory.getLogger("GOKb-clear-warning_command");

	@Override
  	public void doPost(HttpServletRequest request, HttpServletResponse response)throws ServletException, IOException {
	    String text = request.getParameter("text");
	    String title = request.getParameter("title");
    	String json_mock = String.format("{'text':'%s','title':'%s'}",text,title);
    	hide_message(json_mock);
  	}

  	private void hide_message(String json_mock){
  		NotificationStack stack = NotificationStack.get("validation_hidden");
        Notification n= Notification.fromJSON(json_mock, Notification.class);
        stack.add(n);

  	}
}