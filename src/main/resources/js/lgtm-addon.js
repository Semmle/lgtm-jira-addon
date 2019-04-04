var config = null;

var adminFormId = "#admin";

var userFieldId = "#user";
var secretFieldId = "#secret";
var externalHookUrlFieldId = "#externalHookUrl";
var projectFieldId = "#project";

var webhookUrlFieldId = "#incomingHookUrl";
var urlCodeId = "urlCode";
var key = "webhook";

(function() { // this closure helps us keep our variables to ourselves.
// This pattern is known as an "iife" - immediately invoked function expression
	AJS.$(document).ready(function() {
		var url = AJS.params.baseURL + "/plugins/servlet/lgtm/" + key;
		renderWebhookUrl(url)

		// Init select2 fields
		AJS.$(projectFieldId).auiSelect2();

		AJS.$(adminFormId).submit(function(e) {
			e.preventDefault();
			updateConfig();
		});

		// Request the current config from the server
		AJS.$.ajax({
			url : AJS.contextPath() + "/rest/lgtm-config/1.0/",
			dataType : "json"
		}).done(function(current_config) {
			config = current_config;
			AJS.$(userFieldId).val(config.username);
			AJS.$(secretFieldId).val(config.lgtmSecret);
			AJS.$(externalHookUrlFieldId).val(config.externalHookUrl);
			loadProjects();
		});
	});

})();

function renderWebhookUrl(url) {
	AJS.$(webhookUrlFieldId).val(url);
}

function loadProjects() {
	// Get List of projects
	$.ajax({
		url : AJS.contextPath() + "/rest/api/2/project/"
	}).done(function(projects) {
		var fieldContent = projects.map(function(project) {
			return {
				value : project.key,
				text : project.name
			}
		});
		fieldContent.unshift({value : "none", text : ""});
		
		renderSelect2Field(projectFieldId, fieldContent);
		if (config !== null) {
			changeSelect2Value(projectFieldId, config.projectKey);
		}
	});
}

function clearSelect2Field(fieldId) {
	var select2Field = AJS.$(fieldId);
	select2Field.select2('data', null);
	select2Field.empty();
}

function renderSelect2Field(fieldId, content) {
	clearSelect2Field(fieldId);
	
	var select2Field = AJS.$(fieldId);
	
	for (var i = 0; i < content.length; i++) {
		var newOption = document.createElement('option');
		newOption.setAttribute('value', content[i].value);
		newOption.textContent = content[i].text;
		select2Field.append(newOption);
	}

	select2Field.auiSelect2();
}

function changeSelect2Value(fieldId, newValue) {
	var select2Field = AJS.$(fieldId);
	select2Field.select2('val', newValue);
	select2Field.trigger({
		type : 'change'
	});
}

function updateConfig() {
	
	for (var i = 0; i < AJS.$("#message-context").children().length; i++) {
		AJS.$("#message-context").children()[i].remove();
	}
	
	if (AJS.$(secretFieldId).attr("value") === "") {
		AJS.messages.error("#message-context", {
			title : 'Please enter a valid secret.',
			closeable : true,
			fadeout : true
		});
		return;
	}

	var externalHookUrl = AJS.$(externalHookUrlFieldId).attr("value");
	if (externalHookUrl === "") {
		externalHookUrl = null;
	}

	if (AJS.$(userFieldId).attr("value") === "") {
		AJS.messages.error("#message-context", {
			title : 'Please enter a username.',
			closeable : true,
			fadeout : true
		});
		return;
	}

	if (AJS.$(projectFieldId).select2('val') === "none") {
		AJS.messages.error("#message-context", {
			title : 'Please select a project.',
			closeable : true,
			fadeout : true
		});
		return;
	}

	var data = {
		'key' : key,
		'lgtmSecret' : AJS.$('#secret').attr('value'),
		'externalHookUrl' : externalHookUrl,
		'username' : AJS.$('#user').attr('value'),
		'projectKey' : AJS.$('#project').select2('val'),
	};

	AJS.$.ajax({
		url : AJS.contextPath() + "/rest/lgtm-config/1.0/",
		type : "PUT",
		contentType : "application/json",
		data : JSON.stringify(data),
		processData : false
	}).done(function() {
		AJS.messages.success("#message-context", {
			title : 'The configuration was saved succesfully',
			closeable : true,
			fadeout : true
		});
	}).fail(function(jqXHR, textStatus, errorThrown) {
		if (jqXHR.getResponseHeader("Error") === "user-not-found") {
			AJS.messages.error("#message-context", {
				title : 'The user ' + AJS.$(userFieldId).attr("value")
						+ ' does not exist.',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "project-not-found") {
			// This should not happen
			AJS.messages.error("#message-context", {
				title : 'The project "' + AJS.$(projectFieldId).select2('data').text
						+ '" does not exist.',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "issueType-not-found") {
			AJS.messages.error("#message-context", {
				title : 'The "LGTM alert" issue type could not be found.',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "workflow-not-found") {
			AJS.messages.error("#message-context", {
				title : 'The LGTM alert workflow does not exists. Please re-enable add-on.',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "manual-migration-needed") {
			
			var body = '<p>There are existing tickets using an old workflow type, and hence a one-time manual migration is needed.</p>' +
			'<ul>'+
			'<li>Please go to the <a href="' + AJS.params.baseURL + '/secure/admin/ViewWorkflowSchemes.jspa" target="_blank">workflow schemes page</a> '+
				"and click to edit the scheme associated with your project chosen above.</li>" +
			"<li>Select 'Add workflow' followed by 'Add existing'.</li>" +
			"<li>On the first screen select 'LGTM workflow' then on the second select 'LGTM alert'. Click 'Finish'.</li>" +
			"<li>Finally, click 'Publish' and Jira will guide you through mapping tickets to the new workflow.</li>" +
			"</ul>";
			
			AJS.messages.error("#message-context", {
				title : 'A manual workflow migration is needed.',
				body : body,
				closeable : true,
				fadeout : false
			});
			return;
		}
		AJS.messages.error("#message-context", {
			title : 'An error occurred.',
			body : 'Please try again.',
			closeable : true,
			fadeout : true
		});
	});
};
