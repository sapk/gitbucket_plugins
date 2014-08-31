import plugin._

val pluginDef = ScalaPlugin.define(id, version, author, url, description)

pluginDef.addRepositoryAction("GET", "/issues/export", Security.Member()){ (request, response, repository) =>
  db.select(s"""SELECT * FROM ISSUE WHERE
    USER_NAME       = '${repository.owner}' AND
    REPOSITORY_NAME = '${repository.name}'  AND
    PULL_REQUEST    = false""").map { issue =>
    Map(
      "issueId"          -> issue("ISSUE_ID"),
      "title"            -> issue("TITLE"),
      "content"          -> issue("CONTENT"),
      "openedUserName"   -> issue("OPENED_USER_NAME"),
      "assignedUserName" -> issue("ASSIGNED_USER_NAME"),
      "closed"           -> issue("CLOSED"),
      "registeredDate"   -> issue("REGISTERED_DATE"),
      "updatedDate"      -> issue("UPDATED_DATE"),
      "comments"         -> db.select(s"""SELECT * FROM ISSUE_COMMENT WHERE
        USER_NAME       = '${repository.owner}' AND
        REPOSITORY_NAME = '${repository.name}'  AND
        ISSUE_ID        = ${issue("ISSUE_ID")}
        ORDER BY COMMENT_ID""").map { comment =>
          Map(
            "commentId"         -> comment("COMMENT_ID"),
            "action"            -> comment("ACTION"),
            "commentedUserName" -> comment("COMMENTED_USER_NAME"),
            "content"           -> comment("CONTENT"),
            "registeredDate"    -> comment("REGISTERED_DATE"),
            "updatedDate"       -> comment("UPDATED_DATE")
          )
        }
    )
  }
}

pluginDef.addJavaScript((path: String) => path.endsWith("/issues"), """
$("a:contains('New Issue')").before('<a class="btn btn-small" href="issues/export">Export as JSON</a>');
""")

PluginSystem.install(pluginDef)
