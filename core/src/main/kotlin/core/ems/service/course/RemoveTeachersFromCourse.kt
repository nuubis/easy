package core.ems.service.course

import com.fasterxml.jackson.annotation.JsonProperty
import core.conf.security.EasyUser
import core.db.Teacher
import core.db.TeacherCourseAccess
import core.ems.service.idToLongOrInvalidReq
import core.exception.InvalidRequestException
import mu.KotlinLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.*
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/v2")
class RemoveTeachersFromCourseController {

    data class Req(@JsonProperty("teachers") @field:Valid val teacherIds: List<TeacherIdReq>)

    data class TeacherIdReq(@JsonProperty("teacher_id") @field:NotBlank @field:Size(max = 100) val teacherId: String)

    @Secured("ROLE_ADMIN")
    @DeleteMapping("/courses/{courseId}/teachers")
    fun controller(@PathVariable("courseId") courseIdStr: String,
                   @Valid @RequestBody teachers: Req,
                   caller: EasyUser) {

        log.debug { "Removing teachers $teachers from course $courseIdStr" }

        val courseId = courseIdStr.idToLongOrInvalidReq()

        deleteTeachersFromCourse(teachers, courseId)
    }
}


private fun deleteTeachersFromCourse(teachers: RemoveTeachersFromCourseController.Req, courseId: Long) {
    transaction {
        teachers.teacherIds.forEach { teacher ->
            val teacherExists =
                    Teacher.select { Teacher.id eq teacher.teacherId }
                            .count() == 1L
            if (!teacherExists) {
                throw InvalidRequestException("Teacher not found: $teacher")
            }
        }

        val teachersWithAccess = teachers.teacherIds.filter {
            TeacherCourseAccess.select {
                TeacherCourseAccess.teacher eq it.teacherId and (TeacherCourseAccess.course eq courseId)
            }.count() > 0
        }


        teachersWithAccess.forEach { teacher ->
            TeacherCourseAccess.deleteWhere {
                TeacherCourseAccess.teacher eq teacher.teacherId and (TeacherCourseAccess.course eq courseId)
            }
        }

        log.debug { "Removing access from teachers (the rest have already no access): $teachersWithAccess" }

    }
}
