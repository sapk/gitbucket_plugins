var plugin = JavaScriptPlugin.define(id, version, author, url, description);

plugin.addGlobalAction('/test', function(request, response){
  return "<h1>This is Test Plug-In</h1>";
});

PluginSystem.install(plugin);
