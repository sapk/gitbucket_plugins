import plugin._
import util._
import java.io.File
import org.apache.commons.io.IOUtils
import java.io.FileInputStream

val pluginDef = ScalaPlugin.define(id, version, author, url, description)

/* Ajoute la feuille de style globalement */
pluginDef.addJavaScript((path: String) => true, """
$('head').append('<link href="/gitlab/theme.css" rel="stylesheet">').append('<script type="text/javascript" src="/gitlab/script.js"><\/script>');
""")

/**
* Returns the theme.css
*/
pluginDef.addGlobalAction("GET", "/gitlab/theme.css"){ (request, response, context) =>
	val f = new File(s"${Directory.GitBucketHome}/plugins/gitlab/theme.css")
    val bytes = IOUtils.toByteArray(new FileInputStream(f))
    val charset = StringUtil.detectEncoding(bytes)
    response.setHeader("Cache-Control","max-age=604800")
    response.setDateHeader("Expires", System.currentTimeMillis() + 604800000L); // 1 week in future.
	RawData(s"text/css; charset=${charset}", bytes)

}
/**
* Returns the script.jss
*/
pluginDef.addGlobalAction("GET", "/gitlab/script.js"){ (request, response, context) =>
	val f = new File(s"${Directory.GitBucketHome}/plugins/gitlab/script.js")   
    val bytes = IOUtils.toByteArray(new FileInputStream(f))
    val charset = StringUtil.detectEncoding(bytes)
    response.setHeader("Cache-Control","max-age=604800")
    response.setDateHeader("Expires", System.currentTimeMillis() + 604800000L); // 1 week in future.
	RawData(s"application/javascript; charset=${charset}", bytes)
}


PluginSystem.install(pluginDef)
