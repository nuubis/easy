package core.ems.service.exercise

import com.fasterxml.jackson.annotation.JsonProperty
import core.conf.security.EasyUser
import core.db.*
import core.ems.service.*
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.*
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size


@RestController
@RequestMapping("/v2")
class TeacherPostFeedbackController(val adocService: AdocService) {
    private val log = KotlinLogging.logger {}

    @Value("\${easy.core.activity.merge-window.s}")
    private lateinit var mergeWindowInSeconds: String

    data class Req(
        @JsonProperty("feedback_adoc", required = true)
        @field:Size(max = 300000)
        @field:NotBlank
        val feedbackAdoc: String
    )

    @Secured("ROLE_TEACHER", "ROLE_ADMIN")
    @PostMapping("/teacher/courses/{courseId}/exercises/{courseExerciseId}/submissions/{submissionId}/feedback")
    fun controller(
        @PathVariable("courseId") courseIdString: String,
        @PathVariable("courseExerciseId") courseExerciseIdString: String,
        @PathVariable("submissionId") submissionIdString: String,
        @Valid @RequestBody assessment: Req,
        caller: EasyUser
    ) {

        log.info { "Set feedback by teacher ${caller.id} to submission $submissionIdString on course exercise $courseExerciseIdString on course $courseIdString" }

        val (callerId, courseExId, submissionId) = assertAssessmentControllerChecks(
            caller,
            submissionIdString,
            courseExerciseIdString,
            courseIdString,
        )

        insertOrUpdateFeedback(callerId, submissionId, assessment, courseExId)
    }

    private fun insertOrUpdateFeedback(teacherId: String, submissionId: Long, assessment: Req, courseExId: Long) =
        transaction {
            val previousId = getIdIfShouldMerge(submissionId, teacherId, mergeWindowInSeconds.toInt())
            val time =  DateTime.now()

            if (previousId != null) {
                TeacherActivity.update({ TeacherActivity.id eq previousId }) {
                    it[mergeWindowStart] = time
                    it[feedbackAdoc] = assessment.feedbackAdoc
                    it[feedbackHtml] = adocService.adocToHtml(assessment.feedbackAdoc)
                }
            } else {
                TeacherActivity.insert {
                    it[student] = selectStudentBySubmissionId(submissionId)
                    it[courseExercise] = courseExId
                    it[submission] = submissionId
                    it[teacher] = teacherId
                    it[mergeWindowStart] = time
                    it[feedbackAdoc] = assessment.feedbackAdoc
                    it[feedbackHtml] = adocService.adocToHtml(assessment.feedbackAdoc)
                }
            }

            StatsSubmission.update ({ StatsSubmission.submissionId eq submissionId }){
                it[hasEverReceivedTeacherComment] = true
                it[latestTeacherActivityUpdate] = time
                it[latestTeacherPseudonym] = selectPseudonym(teacherId)
            }
        }

    private fun getIdIfShouldMerge(submissionId: Long, teacherId: String, mergeWindow: Int): Long? =
        transaction {
            TeacherActivity.slice(TeacherActivity.id, TeacherActivity.mergeWindowStart)
                .select {
                    TeacherActivity.submission eq submissionId and (TeacherActivity.teacher eq teacherId)
                }.orderBy(TeacherActivity.mergeWindowStart, SortOrder.DESC)
                .firstNotNullOfOrNull {
                    val timeIsInWindow = !it[TeacherActivity.mergeWindowStart].hasSecondsPassed(mergeWindow)
                    val noFeedback = it[TeacherActivity.feedbackAdoc] == null
                    if (timeIsInWindow && noFeedback) it[TeacherActivity.id].value else null
                }
        }

}
