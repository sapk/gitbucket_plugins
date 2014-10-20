import plugin._
import util._
import java.util.Date
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.dircache.DirCache
import model.Account
import org.apache.commons.io.IOUtils
import java.io.FileInputStream

val pluginDef = ScalaPlugin.define(id, version, author, url, description)

/* Ajoute la feuille de style globalement */
/* TODO append git path for conf */
pluginDef.addJavaScript((path: String) => true, """
$('head').append($('<link href="/gitlab/theme.css" rel="stylesheet">')).append('<script src="/gitlab/script.js">');
""")
/**
//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css
//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js
**/
/**
* Returns the theme.css
*/
pluginDef.addGlobalAction("GET", "/gitlab/theme.css"){ (request, response, context) =>
	val themefile = new File(s"${Directory.GitBucketHome}/plugins/gitlab/theme.css")   
    val bytes = IOUtils.toByteArray(new FileInputStream(themefile))
    val charset = StringUtil.detectEncoding(bytes)
	RawData(s"text/css; charset=${charset}", bytes)
}
/**
* Returns the script.jss
*/
pluginDef.addGlobalAction("GET", "/gitlab/script.js"){ (request, response, context) =>
	val themefile = new File(s"${Directory.GitBucketHome}/plugins/gitlab/script.js")   
    val bytes = IOUtils.toByteArray(new FileInputStream(themefile))
    val charset = StringUtil.detectEncoding(bytes)
	RawData(s"text/css; charset=${charset}", bytes)
}
PluginSystem.install(pluginDef)
