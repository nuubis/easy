package pages.sidenav

import Icons
import Role
import cache.BasicCourseInfo
import kotlinx.coroutines.await
import pages.course_exercises.CourseExercisesPage
import pages.exercise.ExercisePage
import pages.exercise_library.CreateExerciseModalComp
import pages.grade_table.GradeTablePage
import pages.participants.ParticipantsPage
import rip.kspar.ezspa.*
import successMessage
import tmRender
import kotlin.js.Promise

class SidenavCourseSectionComp(
    private val activeRole: Role,
    private val courseId: String,
    parent: Component,
    dstId: String,
) : SidenavSectionComp(parent, dstId) {

    private lateinit var courseTitle: String

    private val exercisesItemId = IdGenerator.nextId()
    private val gradesItemId = IdGenerator.nextId()
    private val participantsItemId = IdGenerator.nextId()

    private val newExerciseModal = CreateExerciseModalComp(null, courseId, this, "new-exercise-modal-dst-id")
    private val newExerciseLinkId = IdGenerator.nextId()

    override val children = listOf(newExerciseModal)

    override fun create(): Promise<*> = doInPromise {
        courseTitle = BasicCourseInfo.get(courseId).await().title
    }

    override fun render(): String = tmRender(
        "t-c-sidenav-course-section",
        "courseTitle" to courseTitle,
        "isTeacherOrAdmin" to listOf(Role.TEACHER, Role.ADMIN).contains(activeRole),
        "isAdmin" to (activeRole == Role.ADMIN),
        "exercisesId" to exercisesItemId,
        "gradesId" to gradesItemId,
        "participantsId" to participantsItemId,
        "exercisesLink" to CourseExercisesPage.link(courseId),
        "gradesLink" to GradeTablePage.link(courseId),
        "participantsLink" to ParticipantsPage.link(courseId),
        "exercisesIcon" to Icons.courseExercises,
        "gradesIcon" to Icons.courseGrades,
        "participantsIcon" to Icons.courseParticipants,
        "addExerciseIcon" to Icons.add,
        "newExerciseIcon" to Icons.newExercise,
        "exercisesLabel" to "Ülesanded",
        "gradesLabel" to "Hinded",
        "participantsLabel" to "Osalejad",
        "participantsLabel" to "Osalejad",
        "newExerciseLabel" to "Uus ülesanne",
        "newExerciseLinkId" to newExerciseLinkId,
        "addExerciseLabel" to "Lisa ülesanne kogust",
    )

    override fun postRender() {
        getElemByIdOrNull(newExerciseLinkId)?.onVanillaClick(true) {
            val exerciseId = newExerciseModal.openWithClosePromise().await()
            if (exerciseId != null) {
                EzSpa.PageManager.navigateTo(ExercisePage.link(exerciseId))
                successMessage { "Ülesanne loodud" }
            }
        }
    }

    override fun getActivePageItemIds() = mapOf(
        ActivePage.COURSE_EXERCISES to exercisesItemId,
        ActivePage.COURSE_GRADES to gradesItemId,
        ActivePage.COURSE_PARTICIPANTS to participantsItemId,
    )
}