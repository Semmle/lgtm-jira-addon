var config = null;

var adminFormId = "#admin";

var userFieldId = "#user";
var secretFieldId = "#secret";

var projectFieldId = "#project";
var priorityFieldId = "#priority";

var webhookUrlFieldId = "#webhookUrl";
var urlCodeId = "urlCode";
var key = "webhook";

(function() { // this closure helps us keep our variables to ourselves.
// This pattern is known as an "iife" - immediately invoked function expression
	AJS.$(document).ready(function() {
		var url = AJS.params.baseURL + "/plugins/servlet/lgtm/" + key;
		renderWebhookUrl(url)

		// Init select2 fields
		AJS.$(projectFieldId).auiSelect2();
		AJS.$(priorityFieldId).auiSelect2();

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
			
			loadProjects();
		});
	});

})();

function renderWebhookUrl(url) {
	AJS.$("#"+urlCodeId).remove();
	var urlCode = document.createElement('code');
	urlCode.id = urlCodeId;
	urlCode.textContent = url;
	AJS.$(webhookUrlFieldId).append(urlCode);
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
		AJS.$(projectFieldId).on('change', handleProjectChange);
		if (config !== null) {
			changeSelect2Value(projectFieldId, config.projectKey);
		}
	});
}

function handleProjectChange(event) {
	clearSelect2Field(priorityFieldId);
	
	if (AJS.$(projectFieldId).select2('val') !== "none") {
		loadPriorities(AJS.$(projectFieldId).select2('val'));
	}
}

function loadPriorities(projectKey) {
	AJS.$.ajax({
		url : AJS.contextPath() + "/rest/api/2/priority"
	}).done(function(allPriorities) {
		var idToName = new Map();
		for (var j = 0; j < allPriorities.length; j++) {
			idToName.set(allPriorities[j].id, allPriorities[j].name);
		}
		
		var priorityIds = new Array();
		AJS.$.ajax({
			url : AJS.contextPath() + "/rest/api/2/project/" + projectKey
					+ "/priorityscheme"
		}).done(function(schemes) {
			priorityIds = schemes.optionIds;
		}).fail(function() {
			priorityIds = allPriorities.map(function(priority) {
				return priority.id;
			});
		}).always(function() {
			priorityIds.sort();
			
			var fieldContent = priorityIds.map(function(priorityId) {
				return {
					value : priorityId,
					text : idToName.get(priorityId)
				}
			});
			
			fieldContent.unshift({value : "", text : "--Default--"});
			
			renderSelect2Field(priorityFieldId, fieldContent);
			
			if (config !== null) {
				changeSelect2Value(priorityFieldId, config.priorityLevelId);
			}
		});
	});
};

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
	
	if (AJS.$(secretFieldId).attr("value") === "") {
		AJS.messages.error("#message-context", {
			title : 'Please enter a valid secret.',
			closeable : true,
			fadeout : true
		});
		return;
	}

	if (AJS.$(userFieldId).attr("value") === "") {
		AJS.messages.error("#message-context", {
			title : 'Please enter a valid user.',
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
		'username' : AJS.$('#user').attr('value'),
		'projectKey' : AJS.$('#project').select2('val'),
		'priorityLevelId' : AJS.$('#priority').select2('val')
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
		if (jqXHR.getResponseHeader("Error") === "username") {
			AJS.messages.error("#message-context", {
				title : 'The user ' + AJS.$(userFieldId).attr("value")
						+ ' does not exist.',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "project") {
			// This should not happen
			AJS.messages.error("#message-context", {
				title : 'The project "' + AJS.$(projectFieldId).select2('data').text
						+ '" does not exist.',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "issueType") {
			AJS.messages.error("#message-context", {
				title : 'The "LGTM alert" issue type could not be found.',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "workflow") {
			AJS.messages.error("#message-context", {
				title : 'A problem occurred when adding the LGTM alert workflow to the project.',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "workflow-not-found") {
			AJS.messages.error("#message-context", {
				title : 'The LGTM alert workflow does not exists. Please install it',
				closeable : true,
				fadeout : true
			});
			return;
		} else if (jqXHR.getResponseHeader("Error") === "manual-migration-needed") {
			AJS.messages.error("#message-context", {
				title : 'A manual workflow migration is needed',
				closeable : true,
				fadeout : true
			});
			return;
		}
		AJS.messages.error("#message-context", {
			title : 'An error happened.',
			closeable : true,
			fadeout : true
		});
	});
};
