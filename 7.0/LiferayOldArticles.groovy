import com.liferay.journal.model.JournalArticle
import com.liferay.journal.service.JournalArticleLocalServiceUtil
import com.liferay.portal.kernel.dao.orm.DynamicQuery
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil
import com.liferay.portal.kernel.service.GroupLocalServiceUtil
import com.liferay.portal.kernel.workflow.WorkflowConstants

class OldArticlesManager {

  int leaveVersionCount
  int queryStep
  def o

  private def showOldArticles(boolean remove=false) {
    def totalGroups = GroupLocalServiceUtil.getGroupsCount()
    for (def i = 0; i < totalGroups; i+=queryStep) {
      def groupIds = getGroupIds(i, i + queryStep)
      groupIds.each {groupId ->
        processArticlesByGroupId(remove, groupId)
      }
    }
  }

  private def processArticlesByGroupId = {remove, groupId ->
    o.println "${"*" * 15} Processing group: $groupId ${"*" * 15}"
    def totalArticles = JournalArticleLocalServiceUtil.getArticlesCount(groupId)
    for (def i = 0; i < totalArticles; i+=queryStep) {
      def articleIds = getArticleIds(i, i + queryStep, groupId)
      articleIds.each {articleId ->
        processArticle(groupId, remove, articleId)
      }
    }
  }

  private def processArticle = {groupId, remove, articleId ->
      JournalArticle lastVersion = JournalArticleLocalServiceUtil.fetchLatestArticle(groupId, articleId, WorkflowConstants.STATUS_APPROVED)?:
        JournalArticleLocalServiceUtil.fetchLatestArticle(groupId, articleId, WorkflowConstants.STATUS_ANY)
      if (!lastVersion) return

      double lastVersionApproved = lastVersion.version
      o.println "${"=" * 5} Processing article: $articleId; max version: $lastVersionApproved ${"=" * 5}"
      def allVersionArticles = JournalArticleLocalServiceUtil.getArticles(groupId, articleId).sort(false) {it.version}
      int versionsCount = allVersionArticles.size()
      int countDeleted
      for (def article in allVersionArticles) {
          double version = article.version
          if (versionsCount > leaveVersionCount && version < lastVersionApproved) {
              if (remove) {
                  JournalArticleLocalServiceUtil.deleteArticle(article)
              }
              o.println("Deleted version: " + version + " of JournalArticle: " + articleId)
              versionsCount--; countDeleted++
          }
      }
  }

  private def getGroupIds(start, end) {
    def query = GroupLocalServiceUtil.dynamicQuery()
    query.setLimit(start, end)
    query.setProjection(ProjectionFactoryUtil.property("groupId"))
    return GroupLocalServiceUtil.dynamicQuery(query)
  }

  private def getArticleIds(start, end, groupId) {
    def query = JournalArticleLocalServiceUtil.dynamicQuery()
    query.setProjection(ProjectionFactoryUtil.distinct(
      PropertyFactoryUtil.forName("articleId")))
    query.add(PropertyFactoryUtil.forName("groupId").eq(groupId))
    query.setLimit(start, end)
    return JournalArticleLocalServiceUtil.dynamicQuery(query)
  }
}

def OAM = [leaveVersionCount: 2,queryStep: 100, o: out] as OldArticlesManager
OAM.showOldArticles()
