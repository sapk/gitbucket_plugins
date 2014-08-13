import plugin._

val pluginDef = ScalaPlugin.define(id, version, author, url, description)

pluginDef.addGlobalAction('/gist'){ (request, response) =>
  "<h1>Gist</h1>"
}

PluginSystem.install(pluginDef)
