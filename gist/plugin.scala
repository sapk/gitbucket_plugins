import plugin._
import util._
import java.util.Date
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.dircache.DirCache
import model.Account

val pluginDef = ScalaPlugin.define(id, version, author, url, description)

val GistRepoDir = s"${Directory.GitBucketHome}/gist"

val rootdir = new File(GistRepoDir)
if(!rootdir.exists){
  rootdir.mkdirs()
}

/**
 *
 */
pluginDef.addJavaScript((path: String) => true, """
$('div.navbar div.nav-collapse').prepend($('<a href="/gist" class="username menu">Snippets</a>'));
""")

/**
 * Displays new Gist creation form
 */
pluginDef.addGlobalAction("GET", "/gist"){ (request, response, context) =>
  if(context.loginAccount.isDefined){
    edit(None, Seq(("", JGitUtil.ContentInfo("text", None, Some("UTF-8")))))(context)
  } else {
    val result = db.select("SELECT * FROM GIST WHERE PRIVATE = FALSE ORDER BY REGISTERED_DATE DESC")

    val gists = result.flatMap { gist =>
      val userName = gist("USER_NAME")
      val repoName = gist("REPOSITORY_NAME")
      val gitdir = new File(rootdir, userName + "/" + repoName)
      if(gitdir.exists){
        ControlUtil.using(Git.open(gitdir)){ git =>
          val source: String = JGitUtil.getFileList(git, "master", ".").map { file =>
            StringUtil.convertFromByteArray(JGitUtil.getContentFromId(git, file.id, true).get)
              .split("\n").take(9).mkString("\n")
          }.head

          Some(gist + ("CODE" -> source))
        }
      } else {
        None
      }
    }

    list(gists)(context)
  }
}

/**
 * Displays the Gist editing form
 */
pluginDef.addGlobalAction("GET", "/gist/.*/edit"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  val userName = dim(2)
  val repoName = dim(3)

  if(isEditable(userName, context)){
    val gitdir = new File(rootdir, userName + "/" + repoName)
    if(gitdir.exists){
      ControlUtil.using(Git.open(gitdir)){ git =>
        val gist: Map[String, String] = db.select("""
          SELECT * FROM GIST WHERE USER_NAME = ? AND REPOSITORY_NAME = ?
        """, userName, repoName).head

        val files: Seq[(String, JGitUtil.ContentInfo)] = JGitUtil.getFileList(git, "master", ".").map { file =>
          file.name -> JGitUtil.getContentInfo(git, file.name, file.id)
        }

        edit(Some(gist), files)(context)
      }
    }
  } else {
    // TODO Permission Error
  }
}

/**
 * Returns the HTML fragment for adding file.
 */
pluginDef.addGlobalAction("GET", "/gist/_add"){ (request, response, context) =>
  val count = request.getParameter("count").toInt
  Fragment(editor(count, "", JGitUtil.ContentInfo("text", None, Some("UTF-8")))(context))
}

/**
 * Creates a new Gist
 */
pluginDef.addGlobalAction("POST", "/gist/_new"){ (request, response, context) =>
  if(context.loginAccount.isDefined){
    val loginAccount = context.loginAccount.get
    val files        = getFileParameters(request, true)
    val isPrivate    = request.getParameter("private")
    val description  = request.getParameter("description")

    // Create new repository
    val repoName = StringUtil.md5(loginAccount.userName + " " + view.helpers.datetime(new Date()))
    val gitdir   = new File(rootdir, loginAccount.userName + "/" + repoName)
    gitdir.mkdirs()
    JGitUtil.initRepository(gitdir)

    // Insert record
    db.update("""INSERT INTO GIST (
      USER_NAME,
      REPOSITORY_NAME,
      PRIVATE,
      TITLE,
      DESCRIPTION,
      REGISTERED_DATE,
      UPDATED_DATE
    ) VALUES (
      ?, -- USER_NAME
      ?, -- REPOSITORY_NAME
      ?, -- PRIVATE
      ?, -- TITLE
      ?, -- DESCRIPTION
      CURRENT_TIMESTAMP(),
      CURRENT_TIMESTAMP()
    )""", loginAccount.userName, repoName, isPrivate, files.head._1, description)

    // Commit files
    ControlUtil.using(Git.open(gitdir)){ git =>
      commitFiles(git, loginAccount, "Initial commit", files)
    }

    Redirect(s"/gist/${loginAccount.userName}/${repoName}")
  }
}

pluginDef.addGlobalAction("POST", "/gist/.*/edit"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  val userName = dim(2)
  val repoName = dim(3)

  if(isEditable(userName, context)){
    val loginAccount = context.loginAccount.get
    val files        = getFileParameters(request, true)
    val isPrivate    = request.getParameter("private")
    val description  = request.getParameter("description")
    val gitdir       = new File(rootdir, userName + "/" + repoName)

    // Update record
    db.update("""
      UPDATE GIST SET TITLE = ?, DESCRIPTION  = ?, UPDATED_DATE = CURRENT_TIMESTAMP()
      WHERE USER_NAME = ? AND REPOSITORY_NAME = ?
    """, files.head._1, description, userName, repoName)

    // Commit files
    ControlUtil.using(Git.open(gitdir)){ git =>
      val commitId = commitFiles(git, loginAccount, "Update", files)

      // update refs
      val refUpdate = git.getRepository.updateRef(Constants.HEAD)
      refUpdate.setNewObjectId(commitId)
      refUpdate.setForceUpdate(false)
      refUpdate.setRefLogIdent(new org.eclipse.jgit.lib.PersonIdent(loginAccount.fullName, loginAccount.mailAddress))
      //refUpdate.setRefLogMessage("merged", true)
      refUpdate.update()
    }

    Redirect(s"${context.path}/gist/${loginAccount.userName}/${repoName}")
  } else {
    // TODO Permission Error
  }
}

/**
 * Deletes the specified Gist
 */
pluginDef.addGlobalAction("GET", "/gist/.*/delete"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  val userName = dim(2)
  val repoName = dim(3)

  if(isEditable(userName, context)){
    val loginAccount = context.loginAccount.get
    val gitdir = new File(rootdir, userName + "/" + repoName)

    db.update("DELETE FROM GIST_COMMENT WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName)
    db.update("DELETE FROM GIST WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName)

    org.apache.commons.io.FileUtils.deleteDirectory(gitdir)

    Redirect(s"${context.path}/gist/${userName}")
  }
}

pluginDef.addGlobalAction("GET", "/gist/.*/secret"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  val userName = dim(2)
  val repoName = dim(3)

  if(isEditable(userName, context)){
    db.update("UPDATE GIST SET PRIVATE = TRUE WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName)
  }

  Redirect(s"${context.path}/gist/${userName}/${repoName}")
}

pluginDef.addGlobalAction("GET", "/gist/.*/public"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  val userName = dim(2)
  val repoName = dim(3)

  if(isEditable(userName, context)){
    db.update("UPDATE GIST SET PRIVATE = FALSE WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName)
  }

  Redirect(s"${context.path}/gist/${userName}/${repoName}")
}


/**
 * Displays specified Gist or the list of specified user's Gist
 */
pluginDef.addGlobalAction("GET", "/gist/.*"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  if(dim.length == 3){
    val userName = dim(2)

    val result = if(context.loginAccount.isDefined){
      db.select("""
        SELECT * FROM GIST WHERE USER_NAME = ? AND (USER_NAME = ? OR PRIVATE = FALSE)
        ORDER BY REGISTERED_DATE DESC
      """, userName, context.loginAccount.get.userName)
    } else {
      db.select("""
        SELECT * FROM GIST WHERE USER_NAME = ? AND PRIVATE = FALSE ORDER BY REGISTERED_DATE DESC
      """, userName)
    }

    val gists = result.flatMap { gist =>
      val repoName = gist("REPOSITORY_NAME")
      val gitdir = new File(rootdir, userName + "/" + repoName)
      if(gitdir.exists){
        ControlUtil.using(Git.open(gitdir)){ git =>
          val source: String = JGitUtil.getFileList(git, "master", ".").map { file =>
            StringUtil.convertFromByteArray(JGitUtil.getContentFromId(git, file.id, true).get)
              .split("\n").take(9).mkString("\n")
          }.head

          Some(gist + ("CODE" -> source))
        }
      } else {
        None
      }
    }

    list(gists)(context)

  } else {
    val userName = dim(2)
    val repoName = dim(3)
    val gitdir = new File(rootdir, userName + "/" + repoName)
    if(gitdir.exists){
      ControlUtil.using(Git.open(gitdir)){ git =>
        val gist: Map[String, String] =
          db.select("""
            SELECT * FROM GIST WHERE USER_NAME = ? AND REPOSITORY_NAME = ?
          """, userName, repoName).head

        if(!gist("PRIVATE").toBoolean || context.loginAccount.exists(x => x.isAdmin || x.userName == userName)){
          val files: Seq[(String, String)] = JGitUtil.getFileList(git, "master", ".").map { file =>
            file.name -> StringUtil.convertFromByteArray(JGitUtil.getContentFromId(git, file.id, true).get)
          }

          detail("code", gist, files, isEditable(userName, context))(context)
        } else {
          // TODO Permission Error
        }
      }
    }
  }
}

def getFileParameters(request: javax.servlet.http.HttpServletRequest, flatten: Boolean): Seq[(String, String)] = {
  val count = request.getParameter("count").toInt
  if(flatten){
    (0 to count - 1).flatMap { i =>
      val fileName = request.getParameter(s"fileName-${i}")
      val content  = request.getParameter(s"content-${i}")
      if(fileName.nonEmpty && content.nonEmpty){
        Some((fileName, content))
      } else {
        None
      }
    }
  } else {
    (0 to count - 1).map { i =>
      val fileName = request.getParameter(s"fileName-${i}")
      val content  = request.getParameter(s"content-${i}")
      if(fileName.nonEmpty && content.nonEmpty){
        (fileName, content)
      } else {
        ("", "")
      }
    }
  }
}

def commitFiles(git: Git, loginAccount: Account, message: String, files: Seq[(String, String)]): ObjectId = {
  val builder  = DirCache.newInCore.builder()
  val inserter = git.getRepository.newObjectInserter()
  val headId   = git.getRepository.resolve(Constants.HEAD + "^{commit}")

  files.foreach { case (fileName, content) =>
    builder.add(JGitUtil.createDirCacheEntry(fileName, FileMode.REGULAR_FILE,
      inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8"))))
  }
  builder.finish()

  val commitId = JGitUtil.createNewCommit(git, inserter, headId, builder.getDirCache.writeTree(inserter),
    loginAccount.fullName, loginAccount.mailAddress, message)

  inserter.flush()
  inserter.release()

  commitId
}

def isEditable(userName: String, context: app.Context): Boolean = {
  context.loginAccount.map { loginAccount =>
    loginAccount.isAdmin || loginAccount.userName == userName
  }.getOrElse(false)
}

PluginSystem.install(pluginDef)
