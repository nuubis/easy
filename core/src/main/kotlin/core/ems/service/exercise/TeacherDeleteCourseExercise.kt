package core.ems.service.exercise

import core.conf.security.EasyUser
import core.db.*
import core.ems.service.*
import mu.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/v2")
class TeacherDeleteCourseExercise {
    private val log = KotlinLogging.logger {}

    @Secured("ROLE_TEACHER", "ROLE_ADMIN")
    @DeleteMapping("/courses/{courseId}/exercises/{courseExerciseId}")
    fun controller(
        @PathVariable("courseId") courseIdStr: String,
        @PathVariable("courseExerciseId") courseExIdStr: String,
        caller: EasyUser
    ) {
        log.debug { "Delete course exercise $courseExIdStr on course $courseIdStr by ${caller.id}" }
        val courseId = courseIdStr.idToLongOrInvalidReq()
        val courseExId = courseExIdStr.idToLongOrInvalidReq()

        assertTeacherOrAdminHasAccessToCourse(caller, courseId)
        assertTeacherOrAdminHasNoRestrictedGroupsOnCourse(caller, courseId)
        assertCourseExerciseIsOnCourse(courseExId, courseId, false)

        deleteCourseExercise(courseExId)
    }


    private fun deleteCourseExercise(courseExId: Long) {

        transaction {
            val submissionCount: Long = Submission.select { Submission.courseExercise eq courseExId }.count()

            val exerciseId: Long =
                CourseExercise.slice(CourseExercise.exercise)
                    .select { CourseExercise.id eq courseExId }
                    .map { it[CourseExercise.exercise].value }
                    .single()


            Exercise.update({ Exercise.id eq exerciseId }) {
                it.update(removedSubmissionsCount, removedSubmissionsCount + submissionCount.toInt())
            }

            val submissionsToDelete = Submission
                .slice(Submission.id)
                .select { Submission.courseExercise eq courseExId }
                .map { it[Submission.id].value }


            TeacherAssessment.deleteWhere { TeacherAssessment.submission inList submissionsToDelete }
            AutomaticAssessment.deleteWhere { AutomaticAssessment.submission inList submissionsToDelete }
            Submission.deleteWhere { Submission.courseExercise eq courseExId }
            SubmissionDraft.deleteWhere { SubmissionDraft.courseExercise eq courseExId }
            CourseExercise.deleteWhere { CourseExercise.id eq courseExId }
        }
    }
}