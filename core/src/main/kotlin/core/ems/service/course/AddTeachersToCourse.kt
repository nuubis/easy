package core.ems.service.course

import com.fasterxml.jackson.annotation.JsonProperty
import core.conf.security.EasyUser
import core.db.Account
import core.db.TeacherCourseAccess
import core.db.TeacherCourseGroup
import core.ems.service.access_control.assertAccess
import core.ems.service.access_control.canTeacherAccessCourse
import core.ems.service.access_control.teacherOnCourse
import core.ems.service.assertGroupExistsOnCourse
import core.ems.service.getUsernameByEmail
import core.ems.service.idToLongOrInvalidReq
import core.ems.service.teacherExists
import core.exception.InvalidRequestException
import core.exception.ReqError
import mu.KotlinLogging
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.*
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size


@RestController
@RequestMapping("/v2")
class AddTeachersToCourse {
    private val log = KotlinLogging.logger {}

    data class Req(
        @JsonProperty("teachers") @field:Valid val teachers: List<TeacherReq>
    )

    data class TeacherReq(
        @JsonProperty("email") @field:NotBlank @field:Size(max = 100) val email: String,
        @JsonProperty("groups") @field:Valid val groups: List<GroupReq> = emptyList()
    )

    data class GroupReq(
        @JsonProperty("id") @field:NotBlank @field:Size(max = 100) val groupId: String
    )

    data class Resp(
        @JsonProperty("accesses_added") val accessesAdded: Int
    )

    private data class TeacherNewAccess(val id: String, val email: String, val groups: Set<Long>)

    @Secured("ROLE_TEACHER", "ROLE_ADMIN")
    @PostMapping("/courses/{courseId}/teachers")
    fun controller(
        @PathVariable("courseId") courseIdStr: String,
        @Valid @RequestBody body: Req,
        caller: EasyUser
    ): Resp {

        log.info { "Adding access to course $courseIdStr to teachers $body by ${caller.id}" }
        val courseId = courseIdStr.idToLongOrInvalidReq()

        caller.assertAccess {
            teacherOnCourse(courseId, false)
        }

        val accesses = body.teachers.distinctBy { it.email }.map {
            val id = getUsernameByEmail(it.email)
                ?: throw InvalidRequestException(
                    "Account with email ${it.email} not found",
                    ReqError.ACCOUNT_EMAIL_NOT_FOUND, "email" to it.email
                )
            val groupIds = it.groups.map { it.groupId.idToLongOrInvalidReq() }.toSet()
            TeacherNewAccess(id, it.email, groupIds)
        }

        accesses.flatMap { it.groups }.toSet().forEach {
            assertGroupExistsOnCourse(it, courseId)
        }

        return insertTeacherCourseAccesses(courseId, accesses)
    }


    private fun insertTeacherCourseAccesses(courseId: Long, newTeachers: List<TeacherNewAccess>): Resp {
        val time = DateTime.now()

        val accessesAdded = transaction {

            newTeachers.forEach {
                if (!teacherExists(it.id)) {
                    log.debug { "No teacher entity found for account ${it.id} (email: ${it.email}), creating it" }
                    insertTeacher(it.id)
                }
            }

            val teachersWithoutAccess = newTeachers.filter {
                !canTeacherAccessCourse(it.id, courseId)
            }

            log.debug { "Granting access to teachers (the rest already have access): $teachersWithoutAccess" }

            TeacherCourseAccess.batchInsert(teachersWithoutAccess) {
                this[TeacherCourseAccess.teacher] = it.id
                this[TeacherCourseAccess.course] = courseId
                this[TeacherCourseAccess.createdAt] = time
            }

            teachersWithoutAccess.forEach { teacher ->
                TeacherCourseGroup.batchInsert(teacher.groups) { groupId ->
                    this[TeacherCourseGroup.teacher] = teacher.id
                    this[TeacherCourseGroup.course] = courseId
                    this[TeacherCourseGroup.courseGroup] = groupId
                }
            }

            teachersWithoutAccess.size
        }
        return Resp(accessesAdded)
    }

    private fun insertTeacher(teacherId: String) {
        Account.update({ Account.id eq teacherId }) {
            it[Account.isTeacher] = true
        }
    }
}

