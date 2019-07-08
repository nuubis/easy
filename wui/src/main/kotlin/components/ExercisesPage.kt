package components

import Auth
import DateSerializer
import PageName
import ReqMethod
import Role
import Str
import debug
import debugFunStart
import errorMessage
import fetchEms
import getContainer
import http200
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.list
import objOf
import parseTo
import spa.Page
import tmRender
import kotlin.browser.window
import kotlin.js.Date


object ExercisesPage : Page() {

    enum class GraderType {
        AUTO, TEACHER
    }

    enum class ExerciseStatus {
        UNSTARTED, STARTED, COMPLETED
    }

    @Serializable
    data class CourseInfo(val title: String)

    data class StudentExercise(val id: String,
                               val effective_title: String,
                               @Serializable(with = DateSerializer::class)
                               val deadline: Date?,
                               val status: ExerciseStatus,
                               val grade: Int?,
                               val graded_by: GraderType?,
                               val ordering_idx: Int)

    @Serializable
    data class TeacherExercise(val id: String,
                               val title: String,
                               @Serializable(with = DateSerializer::class)
                               val soft_deadline: Date?,
                               val grader_type: GraderType,
                               val ordering_idx: Int,
                               val unstarted_count: Int,
                               val ungraded_count: Int,
                               val started_count: Int,
                               val completed_count: Int)


    override val pageName: PageName
        get() = PageName.EXERCISES

    override fun pathMatches(path: String) =
            path.matches("^/courses/\\w+/exercises$")


    override fun build(pageStateStr: String?) {
        val funLog = debugFunStart("ExercisesPage.build")

        val courseId = extractSanitizedCourseId(window.location.pathname)
        debug { "Course ID: $courseId" }

        when (Auth.activeRole) {
            Role.STUDENT -> buildStudentExercises(courseId)
            Role.TEACHER, Role.ADMIN -> buildTeacherExercises(courseId)
        }

        funLog?.end()
    }

    override fun destruct() {
        super.destruct()
        Sidenav.remove()
    }

    private fun extractSanitizedCourseId(path: String): String {
        val match = path.match("^/courses/(\\w+)/exercises$")
        if (match != null && match.size == 2) {
            return match[1]
        } else {
            error("Unexpected match on path: ${match?.joinToString()}")
        }
    }

    private fun buildTeacherExercises(courseId: String) {
        Sidenav.build(courseId)

        MainScope().launch {

            val courseInfoPromise = fetchEms("/courses/$courseId/basic", ReqMethod.GET)
            val exercisesPromise = fetchEms("/teacher/courses/$courseId/exercises", ReqMethod.GET)

            val courseInfoResp = courseInfoPromise.await()
            val exercisesResp = exercisesPromise.await()

            if (!courseInfoResp.http200) {
                errorMessage { Str.somethingWentWrong() }
                error("Fetching course info failed with status ${courseInfoResp.status}")
            }
            if (!exercisesResp.http200) {
                errorMessage { Str.somethingWentWrong() }
                error("Fetching exercises failed with status ${exercisesResp.status}")
            }

            val courseTitle = courseInfoResp.parseTo(CourseInfo.serializer()).await().title
            buildHeader(courseTitle)

            val exercises = exercisesResp.parseTo(TeacherExercise.serializer().list).await()

            // TODO
            debug { "exercises: $exercises" }

        }
    }

    private fun buildStudentExercises(courseId: String) {

        // TODO

        getContainer().innerHTML = tmRender("tm-stud-exercises-list", mapOf(
                "title" to "Kursus",
                "exercises" to arrayOf(
                        objOf(
                                "link" to "/",
                                "title" to "1.1 Blbblabla",
                                "deadline" to "1. juuli 2019, 11.11",
                                "evalTeacher" to true,
                                "points" to 99,
                                "completed" to true),
                        objOf(
                                "link" to "/",
                                "title" to "1.2 Yoyoyoyoyo",
                                "deadline" to "2. juuli 2019, 11.11",
                                "evalMissing" to true,
                                "unstarted" to true
                        ),
                        objOf(
                                "link" to "/",
                                "title" to "1.3 Miskeskus",
                                "deadline" to "3. juuli 2019, 11.11",
                                "evalAuto" to true,
                                "points" to 42,
                                "started" to true
                        )
                )
        ))
    }

    private fun buildHeader(courseTitle: String) {
        // TODO
    }

}
