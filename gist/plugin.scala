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

val Limit = 10

/**
 *
 */
pluginDef.addJavaScript((path: String) => true, """
$('a.brand').after($('<span style="float: left; margin-top: 10px;">|&nbsp;&nbsp;&nbsp;&nbsp;<a href="/gist" style="color: black;">Snippet</a></span>'));
""")

/**
 * Displays new Gist creation form
 */
pluginDef.addGlobalAction("GET", "/gist"){ (request, response, context) =>
  if(context.loginAccount.isDefined){
    val gists = db.select("SELECT * FROM GIST WHERE USER_NAME = ? ORDER BY REGISTERED_DATE DESC LIMIT 4",
      context.loginAccount.get.userName).map(toGist)

    edit(gists, None, Seq(("", JGitUtil.ContentInfo("text", None, Some("UTF-8")))))(context)

  } else {
    val page = request.getParameter("page") match {
      case ""|null => 1
      case s => s.toInt
    }
    val result = db.select(
      "SELECT * FROM GIST WHERE PRIVATE = FALSE ORDER BY REGISTERED_DATE DESC LIMIT ? OFFSET ?",
      Limit, (page - 1) * Limit)

    val count = db.select(
      "SELECT COUNT(*) AS COUNT FROM GIST WHERE PRIVATE = FALSE").head.apply("COUNT").toInt

    val gists: Seq[(Gist, String)] = result.map { gist =>
      val userName = gist("USER_NAME")
      val repoName = gist("REPOSITORY_NAME")
      val gitdir = new File(rootdir, userName + "/" + repoName)
      if(gitdir.exists){
        ControlUtil.using(Git.open(gitdir)){ git =>
          val source: String = JGitUtil.getFileList(git, "master", ".").map { file =>
            StringUtil.convertFromByteArray(JGitUtil.getContentFromId(git, file.id, true).get)
              .split("\n").take(9).mkString("\n")
          }.head

          (toGist(gist), source)
        }
      } else {
        (toGist(gist), "Repository is not found!")
      }
    }

    list(None, gists, page, page * Limit < count)(context)
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

        edit(Nil, Some(toGist(gist)), files)(context)
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

/**
 * Updates Gist
 */
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

/**
 * Swicth Gist to secret
 */
pluginDef.addGlobalAction("GET", "/gist/.*/secret"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  val userName = dim(2)
  val repoName = dim(3)

  if(isEditable(userName, context)){
    db.update("UPDATE GIST SET PRIVATE = TRUE WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName)
  }

  Redirect(s"${context.path}/gist/${userName}/${repoName}")
}

/**
 * Swicth Gist to public
 */
pluginDef.addGlobalAction("GET", "/gist/.*/public"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  val userName = dim(2)
  val repoName = dim(3)

  if(isEditable(userName, context)){
    db.update("UPDATE GIST SET PRIVATE = FALSE WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName)
  }

  Redirect(s"${context.path}/gist/${userName}/${repoName}")
}

pluginDef.addGlobalAction("GET", "/gist/.*/raw/.*"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  val userName = dim(2)
  val repoName = dim(3)
  val fileName = dim(5)

  val gitdir = new File(rootdir, userName + "/" + repoName)
  if(gitdir.exists){
    ControlUtil.using(Git.open(gitdir)){ git =>
      JGitUtil.getFileList(git, "master", ".").collectFirst { case file if(file.name == fileName) =>
        val bytes = JGitUtil.getContentFromId(git, file.id, true).get
        val charset = StringUtil.detectEncoding(bytes)
        RawData(s"text/plain; charset=${charset}", bytes)
      }
    }
  } else None
}

/**
 * Displays specified Gist or the list of specified user's Gist
 */
pluginDef.addGlobalAction("GET", "/gist/.*"){ (request, response, context) =>
  val dim = request.getRequestURI.split("/")
  if(dim.length == 3){
    val userName = dim(2)

    val page = request.getParameter("page") match {
      case ""|null => 1
      case s => s.toInt
    }

    val result: (Seq[Map[String, String]], Int)  = if(context.loginAccount.isDefined){
      val gists = db.select("""
        SELECT * FROM GIST WHERE USER_NAME = ? AND (USER_NAME = ? OR PRIVATE = FALSE)
        ORDER BY REGISTERED_DATE DESC LIMIT ? OFFSET ?
      """, userName, context.loginAccount.get.userName, Limit, (page - 1) * Limit)

      val count = db.select("""
        SELECT COUNT(*) AS COUNT FROM GIST WHERE USER_NAME = ? AND (USER_NAME = ? OR PRIVATE = FALSE)
      """, userName, context.loginAccount.get.userName).head.apply("COUNT").toInt

      (gists, count)

    } else {
      val gists = db.select("""
        SELECT * FROM GIST WHERE USER_NAME = ? AND PRIVATE = FALSE ORDER BY REGISTERED_DATE DESC
        LIMIT ? OFFSET ?
      """, userName, Limit, (page - 1) * Limit)

      val count = db.select("""
        SELECT COUNT(*) AS COUNT FROM GIST WHERE USER_NAME = ? AND PRIVATE = FALSE
      """, userName).head.apply("COUNT").toInt

      (gists, count)
    }

    val gists: Seq[(Gist, String)] = result._1.map { gist =>
      val repoName = gist("REPOSITORY_NAME")
      val gitdir = new File(rootdir, userName + "/" + repoName)
      if(gitdir.exists){
        ControlUtil.using(Git.open(gitdir)){ git =>
          val source: String = JGitUtil.getFileList(git, "master", ".").map { file =>
            StringUtil.convertFromByteArray(JGitUtil.getContentFromId(git, file.id, true).get)
              .split("\n").take(9).mkString("\n")
          }.head

          (toGist(gist), source)
        }
      } else {
        (toGist(gist), "Repository is not found!")
      }
    }

    val fullName = db.select("SELECT FULL_NAME FROM ACCOUNT WHERE USER_NAME = ?", userName).head.apply("FULL_NAME")

    list(Some(GistUser(userName, fullName)), gists, page, page * Limit < result._2)(context) // TODO Paging

  } else {
    val userName = dim(2)
    val repoName = dim(3)
    val gitdir = new File(rootdir, userName + "/" + repoName)
    if(gitdir.exists){
      ControlUtil.using(Git.open(gitdir)){ git =>
        val gist = toGist(db.select(
          "SELECT * FROM GIST WHERE USER_NAME = ? AND REPOSITORY_NAME = ?", userName, repoName).head)

        if(!gist.isPrivate || context.loginAccount.exists(x => x.isAdmin || x.userName == userName)){
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

case class GistUser(userName: String, fullName: String)

case class Gist(
  userName: String,
  repositoryName: String,
  isPrivate: Boolean,
  title: String,
  description: String,
  registeredDate: String,
  updatedDate: String
)

def toGist(map: Map[String, String]): Gist =
  Gist(
    userName       = map("USER_NAME"),
    repositoryName = map("REPOSITORY_NAME"),
    isPrivate      = map("PRIVATE").toBoolean,
    title          = map("TITLE"),
    description    = map("DESCRIPTION"),
    registeredDate = map("REGISTERED_DATE"),
    updatedDate    = map("UPDATED_DATE")
  )

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
    Constants.HEAD, loginAccount.fullName, loginAccount.mailAddress, message)

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
