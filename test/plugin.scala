import plugin._

val pluginDef = ScalaPlugin.define(id, version, author, url, description)

pluginDef.addGlobalAction("GET", "/test"){ (request, response) =>
  "<h1>This is Test Plug-In</h1>"
}

PluginSystem.install(pluginDef)
