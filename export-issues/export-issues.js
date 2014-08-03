var plugin = JavaScriptPlugin.define(id, version, author, url, description);

// plugin.addRepositoryAction('/export', function(request, response){
//   return '<h1>Export Issues</h1>' +
//       '<p>Export all issues in this repository as JSON format.</p>' +
//       '<form method="POST" action="doexport"><input type="submit" value="Export"></form>';
// });

plugin.addRepositoryAction('/issues/export', function(request, response, repository){
  var issues = plugin.db().select("SELECT * FROM ISSUE WHERE "+
      "USER_NAME       ='" + repository.owner() + "' AND " +
      "REPOSITORY_NAME ='" + repository.name()  + "' AND " +
      "PULL_REQUEST = false");

  var result = [];

  for(var i = 0; i < issues.length; i++){
    result[i] = {
      issueId          : '' + issues[i].get('ISSUE_ID'),
      title            : '' + issues[i].get('TITLE'),
      content          : '' + issues[i].get('CONTENT'),
      openedUserName   : '' + issues[i].get('OPENED_USER_NAME'),
      assignedUserName : '' + issues[i].get('ASSIGNED_USER_NAME'),
      closed           : '' + issues[i].get('CLOSED'),
      registeredDate   : '' + issues[i].get('REGISTERED_DATE'),
      updatedDate      : '' + issues[i].get('UPDATED_DATE')
    };
  }

  return {
    format: 'json',
    body: JSON.stringify(result)
  };
});

//plugin.addRepositoryMenu('Export', 'export', '/export', '', function(){ return true; });

PluginSystem.install(plugin);
