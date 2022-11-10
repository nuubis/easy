package pages.participants

import Auth
import CONTENT_CONTAINER_ID
import PageName
import Role
import kotlinx.coroutines.await
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import pages.EasyPage
import pages.sidenav.ActivePage
import pages.sidenav.Sidenav
import rip.kspar.ezspa.doInPromise
import rip.kspar.ezspa.getHtml


object ParticipantsPage : EasyPage() {

    override val pageName = PageName.PARTICIPANTS

    override val allowedRoles = listOf(Role.ADMIN, Role.TEACHER)

    override val sidenavSpec: Sidenav.Spec
        get() = Sidenav.Spec(
            courseId, ActivePage.COURSE_PARTICIPANTS
        )

    override val pathSchema = "/courses/{courseId}/participants"

    private val courseId: String
        get() = parsePathParams()["courseId"]


    override fun build(pageStateStr: String?) {
        super.build(pageStateStr)

        getHtml().addClass("wui3")

        doInPromise {
            ParticipantsRootComp(courseId, Auth.activeRole == Role.ADMIN, CONTENT_CONTAINER_ID)
                .createAndBuild().await()
        }
    }

    override fun destruct() {
        super.destruct()
        getHtml().removeClass("wui3")
    }

    fun link(courseId: String) = constructPathLink(mapOf("courseId" to courseId))
}