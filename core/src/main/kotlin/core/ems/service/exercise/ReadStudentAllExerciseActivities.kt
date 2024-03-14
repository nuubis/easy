package core.ems.service.exercise

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import core.conf.security.EasyUser
import core.db.AutogradeActivity
import core.db.Submission
import core.db.TeacherActivity
import core.ems.service.TeacherResp
import core.ems.service.access_control.assertAccess
import core.ems.service.access_control.teacherOnCourse
import core.ems.service.idToLongOrInvalidReq
import core.ems.service.selectTeacher
import core.util.DateTimeSerializer
import mu.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v2")
class ReadStudentAllExerciseActivities {
    private val log = KotlinLogging.logger {}

    data class FeedbackResp(
        @JsonProperty("feedback_html") val feedbackHtml: String, @JsonProperty("feedback_adoc") val feedbackAdoc: String
    )

    data class TeacherActivityResp(
        @JsonProperty("submission_id") val submissionId: Long,
        @JsonProperty("submission_number") val submissionNumber: Int,
        @JsonProperty("created_at") @JsonSerialize(using = DateTimeSerializer::class) val createdAt: DateTime,
        @JsonProperty("grade") val grade: Int?,
        @JsonProperty("edited_at") @JsonSerialize(using = DateTimeSerializer::class) val editedAt: DateTime?,
        @JsonProperty("feedback") val feedback: FeedbackResp?,
        @JsonProperty("teacher") val teacher: TeacherResp
    )

    data class AutomaticAssessmentResp(
        @JsonProperty("submission_id") val submissionId: Long,
        @JsonProperty("grade") val grade: Int,
        @JsonProperty("feedback") val feedback: String?
    )

    data class Resp(
        @JsonProperty("teacher_activities") val teacherActivities: List<TeacherActivityResp>,
        @JsonProperty("auto_assessments") val autoAssessments: List<AutomaticAssessmentResp>
    )

    @Secured("ROLE_TEACHER", "ROLE_ADMIN")
    @GetMapping("/teacher/courses/{courseId}/exercises/{courseExerciseId}/students/{studentId}/activities")
    fun controller(
        @PathVariable("courseId") courseIdString: String,
        @PathVariable("courseExerciseId") courseExerciseIdString: String,
        @PathVariable("studentId") studentId: String,
        caller: EasyUser
    ): Resp {

        log.info { "Getting activities for ${caller.id} by $studentId on course exercise $courseExerciseIdString on course $courseIdString" }
        val courseId = courseIdString.idToLongOrInvalidReq()
        val courseExId = courseExerciseIdString.idToLongOrInvalidReq()

        caller.assertAccess { teacherOnCourse(courseId) }

        return selectTeacherAllSubmissions(courseExId, studentId)
    }

    private fun selectTeacherAllSubmissions(courseExId: Long, studentId: String): Resp = transaction {
        val teacherActivities = (Submission innerJoin TeacherActivity)
            .select(
                TeacherActivity.submission,
                TeacherActivity.feedbackHtml,
                TeacherActivity.feedbackAdoc,
                TeacherActivity.mergeWindowStart,
                TeacherActivity.grade,
                TeacherActivity.editedAt,
                TeacherActivity.teacher,
                Submission.number
            ).where {
                TeacherActivity.student eq studentId and (TeacherActivity.courseExercise eq courseExId) and (Submission.id eq TeacherActivity.submission)
            }
            .orderBy(TeacherActivity.mergeWindowStart, SortOrder.ASC)
            .map {
                val submissionId = it[TeacherActivity.submission].value
                val html = it[TeacherActivity.feedbackHtml]
                val adoc = it[TeacherActivity.feedbackAdoc]

                TeacherActivityResp(
                    submissionId,
                    it[Submission.number],
                    it[TeacherActivity.mergeWindowStart],
                    it[TeacherActivity.grade],
                    it[TeacherActivity.editedAt],
                    if (html != null && adoc != null) FeedbackResp(html, adoc) else null,
                    selectTeacher(it[TeacherActivity.teacher].value)
                )
            }

        val autoAssessments = AutogradeActivity
            .select(
                AutogradeActivity.submission,
                AutogradeActivity.grade,
                AutogradeActivity.feedback
            ).where { AutogradeActivity.student eq studentId and (AutogradeActivity.courseExercise eq courseExId) }
            .orderBy(AutogradeActivity.createdAt to SortOrder.ASC)
            .map {
                AutomaticAssessmentResp(
                    it[AutogradeActivity.submission].value,
                    it[AutogradeActivity.grade],
                    it[AutogradeActivity.feedback]
                )
            }

        Resp(teacherActivities, autoAssessments)
    }
}