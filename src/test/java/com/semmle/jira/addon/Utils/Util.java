package com.semmle.jira.addon.Utils;

import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Alert;
import com.semmle.jira.addon.Request.Project;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.Request.Alert.Query;

public class Util {
	public static Request createRequest(String projectName, String queryName, String alertFile, String alertMessage) {
		Transition transition = Transition.CREATE;
		String url = "www."+projectName+".com";
		Project project = new Project(1l, "g/"+projectName, projectName, url);
		Query query = new Query(queryName, url+"/"+queryName);
		Alert alert = new Alert(alertFile, alertMessage, url+"/alert", query);
		
		return new Request(transition, 1l, project, alert);
	}
}
