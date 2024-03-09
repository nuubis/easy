package core.ems.service.exercise

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import core.conf.security.EasyUser
import core.db.*
import core.ems.service.GradeResp
import core.ems.service.access_control.assertAccess
import core.ems.service.access_control.studentOnCourse
import core.ems.service.idToLongOrInvalidReq
import core.ems.service.toGradeRespOrNull
import core.util.DateTimeSerializer
import mu.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/v2")
class StudentReadExercisesController {
    private val log = KotlinLogging.logger {}

    data class ExerciseResp(
        @JsonProperty("id") val courseExId: String,
        @JsonProperty("effective_title") val title: String,
        @JsonProperty("grader_type") val graderType: GraderType,
        @JsonSerialize(using = DateTimeSerializer::class)
        @JsonProperty("deadline") val softDeadline: DateTime?,
        @JsonProperty("is_open") val isOpenForSubmissions: Boolean,
        @JsonProperty("status") val status: StudentExerciseStatus,
        @JsonProperty("grade") val grade: GradeResp?,
        @JsonProperty("ordering_idx") val orderingIndex: Int
    )

    data class Resp(@JsonProperty("exercises") val exercises: List<ExerciseResp>)

    @Secured("ROLE_STUDENT")
    @GetMapping("/student/courses/{courseId}/exercises")
    fun controller(@PathVariable("courseId") courseIdStr: String, caller: EasyUser): Resp {

        log.info { "Getting exercises for student ${caller.id}" }
        val courseId = courseIdStr.idToLongOrInvalidReq()

        caller.assertAccess { studentOnCourse(courseId) }
        return selectStudentExercises(courseId, caller.id)
    }

    private fun selectStudentExercises(courseId: Long, studentId: String): Resp = transaction {

        data class ExercisePartial(
            val courseExId: Long,
            val title: String,
            val graderType: GraderType,
            val deadline: DateTime?,
            val isOpen: Boolean,
            val threshold: Int,
            val titleAlias: String?
        )

        val exercisePartials = (CourseExercise innerJoin Exercise innerJoin ExerciseVer)
            .slice(
                ExerciseVer.title,
                ExerciseVer.graderType,
                CourseExercise.id,
                CourseExercise.softDeadline,
                CourseExercise.hardDeadline,
                CourseExercise.gradeThreshold,
                CourseExercise.orderIdx,
                CourseExercise.titleAlias
            )
            .select {
                CourseExercise.course eq courseId and
                        ExerciseVer.validTo.isNull() and
                        CourseExercise.studentVisibleFrom.isNotNull() and
                        CourseExercise.studentVisibleFrom.lessEq(DateTime.now())
            }
            .orderBy(CourseExercise.orderIdx, SortOrder.ASC)
            .map {

                val hardDeadline = it[CourseExercise.hardDeadline]
                ExercisePartial(
                    it[CourseExercise.id].value,
                    it[ExerciseVer.title],
                    it[ExerciseVer.graderType],
                    it[CourseExercise.softDeadline],
                    hardDeadline == null || hardDeadline.isAfterNow,
                    it[CourseExercise.gradeThreshold],
                    it[CourseExercise.titleAlias]
                )
            }

        data class SubmissionPartial(
            val courseExId: Long,
            val gradeResp: GradeResp?,
            val createdAt: DateTime
        )

        val submissions: Map<Long, SubmissionPartial> =
            Submission
                .slice(
                    Submission.courseExercise,
                    Submission.grade,
                    Submission.isAutoGrade,
                    Submission.createdAt,
                    Submission.isGradedDirectly
                )
                .select {
                    Submission.courseExercise inList (exercisePartials.map { it.courseExId }) and (Submission.student eq studentId)
                }
                .map {
                    SubmissionPartial(
                        it[Submission.courseExercise].value,
                        toGradeRespOrNull(
                            it[Submission.grade],
                            it[Submission.isAutoGrade],
                            it[Submission.isGradedDirectly]
                        ),
                        it[Submission.createdAt]
                    )
                }
                .groupBy { it.courseExId }
                .mapValues { exToSubEntry -> exToSubEntry.value.maxBy { subPartial -> subPartial.createdAt } }

        Resp(
            exercisePartials.mapIndexed { i, ex ->
                val lastSub: SubmissionPartial? = submissions[ex.courseExId]
                val grade = lastSub?.gradeResp?.grade
                val status = getStudentExerciseStatus(lastSub != null, grade, ex.threshold)

                ExerciseResp(
                    ex.courseExId.toString(),
                    ex.titleAlias ?: ex.title,
                    ex.graderType,
                    ex.deadline,
                    ex.isOpen,
                    status,
                    lastSub?.gradeResp,
                    i
                )
            }
        )
    }
}

fun getStudentExerciseStatus(hasSubmission: Boolean, grade: Int?, threshold: Int) = when {
    !hasSubmission -> StudentExerciseStatus.UNSTARTED
    grade == null -> StudentExerciseStatus.UNGRADED
    grade >= threshold -> StudentExerciseStatus.COMPLETED
    else -> StudentExerciseStatus.STARTED
}

