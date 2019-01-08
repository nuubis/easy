package ee.urgas.ems.bl.exercise

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import ee.urgas.ems.bl.access.canTeacherAccessCourse
import ee.urgas.ems.db.AutomaticAssessment
import ee.urgas.ems.db.Course
import ee.urgas.ems.db.CourseExercise
import ee.urgas.ems.db.GraderType
import ee.urgas.ems.db.Student
import ee.urgas.ems.db.Submission
import ee.urgas.ems.db.TeacherAssessment
import ee.urgas.ems.exception.ForbiddenException
import ee.urgas.ems.util.DateTimeSerializer
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/v1")
class TeacherReadSubmissionSummariesController {

    data class SubmissionSummaryResp(@JsonProperty("given_name") val studentGivenName: String,
                                     @JsonProperty("family_name") val studentFamilyName: String,
                                     @JsonSerialize(using = DateTimeSerializer::class)
                                     @JsonProperty("submission_time") val submissionTime: DateTime,
                                     @JsonProperty("grade") val grade: Int?,
                                     @JsonProperty("graded_by") val gradedBy: GraderType?)

    @GetMapping("/teacher/courses/{courseId}/exercises/{courseExerciseId}/submissions/latest/students")
    fun readSubmissionSummaries(@PathVariable("courseId") courseIdString: String,
                                @PathVariable("courseExerciseId") courseExerciseIdString: String): List<SubmissionSummaryResp> {

        // TODO: get from auth
        val callerEmail = "ford"
        val courseId = courseIdString.toLong()

        if (!canTeacherAccessCourse(callerEmail, courseId)) {
            throw ForbiddenException("Teacher $callerEmail does not have access to course $courseId")
        }

        return mapToSubmissionSummaryResp(selectTeacherSubmissionSummaries(courseId, courseExerciseIdString.toLong()))
    }

    private fun mapToSubmissionSummaryResp(submissions: List<TeacherSubmissionSummary>) =
            submissions.map {
                SubmissionSummaryResp(
                        it.studentGivenName, it.studentFamilyName, it.submissionTime, it.grade, it.gradedBy
                )
            }
}


data class TeacherSubmissionSummary(val studentGivenName: String, val studentFamilyName: String,
                                    val submissionTime: DateTime, val grade: Int?, val gradedBy: GraderType?)


private fun selectTeacherSubmissionSummaries(courseId: Long, courseExId: Long): List<TeacherSubmissionSummary> {
    return transaction {

        data class SubmissionPartial(val id: Long, val email: String, val createdAt: DateTime)

        // student_id -> submission
        val lastSubmissions = HashMap<String, SubmissionPartial>()

        (Course innerJoin CourseExercise innerJoin Submission)
                .slice(Course.id, CourseExercise.id, Submission.id, Submission.student, Submission.createdAt)
                .select { Course.id eq courseId and (CourseExercise.id eq courseExId) }
                .map {
                    SubmissionPartial(
                            it[Submission.id].value,
                            it[Submission.student].value,
                            it[Submission.createdAt]
                    )
                }
                .forEach {
                    val lastSub = lastSubmissions[it.email]
                    if (lastSub == null || lastSub.createdAt.isBefore(it.createdAt)) {
                        lastSubmissions[it.email] = it
                    }
                }

        lastSubmissions.map { (email, sub) ->

            val studentName = selectStudentName(email)

            var gradedBy: GraderType? = null
            var grade = selectTeacherGrade(sub.id)

            if (grade != null) {
                gradedBy = GraderType.TEACHER
            } else {
                grade = selectAutoGrade(sub.id)
                if (grade != null) {
                    gradedBy = GraderType.AUTO
                }
            }

            TeacherSubmissionSummary(
                    studentName.first,
                    studentName.second,
                    sub.createdAt,
                    grade,
                    gradedBy
            )
        }

    }
}

private fun selectAutoGrade(submissionId: Long): Int? {
    return AutomaticAssessment.select { AutomaticAssessment.submission eq submissionId }
            .orderBy(AutomaticAssessment.createdAt to false)
            .limit(1)
            .map { it[AutomaticAssessment.grade] }
            .firstOrNull()
}

private fun selectTeacherGrade(submissionId: Long): Int? {
    return TeacherAssessment.select { TeacherAssessment.submission eq submissionId }
            .orderBy(TeacherAssessment.createdAt to false)
            .limit(1)
            .map { it[TeacherAssessment.grade] }
            .firstOrNull()
}

private fun selectStudentName(studentEmail: String): Pair<String, String> =
        Student.select { Student.id eq studentEmail }
                .map { Pair(it[Student.givenName], it[Student.familyName]) }
                .single()