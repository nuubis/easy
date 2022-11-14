package core.ems.service.exercise

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import core.aas.selectAutoExercise
import core.conf.security.EasyUser
import core.db.*
import core.ems.service.getImplicitDirFromExercise
import core.ems.service.idToLongOrInvalidReq
import core.exception.InvalidRequestException
import core.util.DateTimeSerializer
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/v2")
class ReadExercise {

    data class Resp(
        @JsonProperty("dir_id") val implicitDirId: String,
        @JsonSerialize(using = DateTimeSerializer::class)
        @JsonProperty("created_at") val created_at: DateTime,
        @JsonProperty("is_public") val public: Boolean,
        @JsonProperty("is_anonymous_autoassess_enabled") val anonymousAutoassessEnabled: Boolean,
        @JsonProperty("owner_id") val ownerUsername: String,
        @JsonSerialize(using = DateTimeSerializer::class)
        @JsonProperty("last_modified") val lastModified: DateTime,
        @JsonProperty("last_modified_by_id") val lastModifiedBy: String,
        @JsonProperty("grader_type") val grader: GraderType,
        @JsonProperty("title") val title: String,
        @JsonProperty("text_html") val textHtml: String?,
        @JsonProperty("text_adoc") val textAdoc: String?,
        @JsonProperty("anonymous_autoassess_template") val anonymousAutoassessTemplate: String?,
        @JsonProperty("grading_script") val gradingScript: String?,
        @JsonProperty("container_image") val containerImage: String?,
        @JsonProperty("max_time_sec") val maxTime: Int?,
        @JsonProperty("max_mem_mb") val maxMem: Int?,
        @JsonProperty("assets") val assets: List<RespAsset>?,
        @JsonProperty("executors") val executors: List<RespExecutor>?,
        @JsonProperty("on_courses") val courses: List<RespCourse>,
        @JsonProperty("on_courses_no_access") val coursesNoAccessCount: Int,
        @JsonProperty("successful_anonymous_submission_count") val successfulAnonymousSubmissionCount: Int,
        @JsonProperty("unsuccessful_anonymous_submission_count") val unsuccessfulAnonymousSubmissionCount: Int,
    )

    data class RespAsset(
        @JsonProperty("file_name") val fileName: String,
        @JsonProperty("file_content") val fileContent: String
    )

    data class RespExecutor(
        @JsonProperty("id") val id: String,
        @JsonProperty("name") val name: String
    )

    data class RespCourse(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("course_exercise_id") val courseExId: String,
        @JsonProperty("course_exercise_title_alias") val titleAlias: String?
    )

    @Secured("ROLE_TEACHER", "ROLE_ADMIN")
    @GetMapping("/exercises/{exerciseId}")
    fun controller(
        @PathVariable("exerciseId") exIdString: String,
        caller: EasyUser
    ): Resp {

        log.debug { "Read exercise $exIdString by ${caller.id}" }
        val exerciseId = exIdString.idToLongOrInvalidReq()

        return selectExerciseDetails(exerciseId, caller)
            ?: throw InvalidRequestException("No exercise found with id $exerciseId")
    }

    private fun selectExerciseDetails(exerciseId: Long, caller: EasyUser): Resp? {
        data class UsedOnCourse(
            val id: String, val title: String, val courseExId: String, val titleAlias: String?,
            val callerHasAccess: Boolean,
        )

        return transaction {

            val usedOnCourses = (CourseExercise innerJoin Course leftJoin TeacherCourseAccess)
                .slice(
                    Course.id, Course.title, CourseExercise.id, CourseExercise.titleAlias,
                    TeacherCourseAccess.teacher
                )
                .select {
                    CourseExercise.exercise eq exerciseId and
                            (TeacherCourseAccess.teacher eq caller.id or TeacherCourseAccess.teacher.isNull())
                }.map {
                    @Suppress("SENSELESS_COMPARISON") // leftJoin
                    UsedOnCourse(
                        it[Course.id].value.toString(),
                        it[Course.title],
                        it[CourseExercise.id].value.toString(),
                        it[CourseExercise.titleAlias],
                        it[TeacherCourseAccess.teacher] != null,
                    )
                }

            val (onCoursesAccess, onCoursesNoAccess) =
                usedOnCourses.partition { it.callerHasAccess || caller.isAdmin() }

            (Exercise innerJoin ExerciseVer)
                .slice(
                    Exercise.createdAt,
                    Exercise.public,
                    Exercise.owner,
                    ExerciseVer.validFrom,
                    ExerciseVer.author,
                    ExerciseVer.graderType,
                    ExerciseVer.title,
                    ExerciseVer.textHtml,
                    ExerciseVer.textAdoc,
                    ExerciseVer.autoExerciseId,
                    Exercise.anonymousAutoassessEnabled,
                    Exercise.anonymousAutoassessTemplate,
                    Exercise.successfulAnonymousSubmissionCount,
                    Exercise.unsuccessfulAnonymousSubmissionCount,
                )
                .select {
                    Exercise.id eq exerciseId and
                            ExerciseVer.validTo.isNull()
                }.map {
                    val graderType = it[ExerciseVer.graderType]
                    val autoExercise =
                        if (graderType == GraderType.AUTO) {
                            val autoExerciseId = it[ExerciseVer.autoExerciseId]
                                ?: throw IllegalStateException("Exercise grader type is AUTO but auto exercise id is null")
                            selectAutoExercise(autoExerciseId)
                        } else null

                    Resp(
                        getImplicitDirFromExercise(exerciseId).toString(),
                        it[Exercise.createdAt],
                        it[Exercise.public],
                        it[Exercise.anonymousAutoassessEnabled],
                        it[Exercise.owner].value,
                        it[ExerciseVer.validFrom],
                        it[ExerciseVer.author].value,
                        it[ExerciseVer.graderType],
                        it[ExerciseVer.title],
                        it[ExerciseVer.textHtml],
                        it[ExerciseVer.textAdoc],
                        it[Exercise.anonymousAutoassessTemplate],
                        autoExercise?.gradingScript,
                        autoExercise?.containerImage,
                        autoExercise?.maxTime,
                        autoExercise?.maxMem,
                        autoExercise?.assets?.map { RespAsset(it.first, it.second) },
                        autoExercise?.executors?.map { RespExecutor(it.id.toString(), it.name) },
                        onCoursesAccess.map { RespCourse(it.id, it.title, it.courseExId, it.titleAlias) },
                        onCoursesNoAccess.count(),
                        it[Exercise.successfulAnonymousSubmissionCount],
                        it[Exercise.unsuccessfulAnonymousSubmissionCount],
                    )
                }.singleOrNull()
        }
    }
}


