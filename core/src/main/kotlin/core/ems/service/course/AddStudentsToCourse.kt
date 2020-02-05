package core.ems.service.course

import com.fasterxml.jackson.annotation.JsonProperty
import core.conf.security.EasyUser
import core.db.*
import core.ems.service.assertGroupExistsOnCourse
import core.ems.service.assertTeacherOrAdminHasAccessToCourse
import core.ems.service.assertTeacherOrAdminHasAccessToCourseGroup
import core.ems.service.idToLongOrInvalidReq
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.*
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/v2")
class AddStudentsToCourseController {

    data class Req(@JsonProperty("students") @field:Valid val students: List<StudentEmailReq>)

    data class StudentEmailReq(
            @JsonProperty("email") @field:NotBlank @field:Size(max = 100) val studentEmail: String,
            @JsonProperty("groups") @field:Valid val groups: List<GroupReq> = emptyList())

    data class GroupReq(@JsonProperty("id") @field:NotBlank @field:Size(max = 100) val groupId: String)

    data class Resp(@JsonProperty("accesses_added") val accessesAdded: Int,
                    @JsonProperty("pending_accesses_added_updated") val pendingAccessesAddedUpdated: Int)

    @Secured("ROLE_TEACHER", "ROLE_ADMIN")
    @PostMapping("/courses/{courseId}/students")
    fun controller(@PathVariable("courseId") courseIdStr: String,
                   @RequestBody @Valid body: Req, caller: EasyUser): Resp {

        log.debug { "Adding access to course $courseIdStr to students $body" }
        val courseId = courseIdStr.idToLongOrInvalidReq()

        assertTeacherOrAdminHasAccessToCourse(caller, courseId)

        val students = body.students.map {
            StudentNoAccount(
                    it.studentEmail.toLowerCase(),
                    it.groups.map {
                        it.groupId.idToLongOrInvalidReq()
                    }.toSet()
            )
        }.distinctBy { it.email }

        students.flatMap { it.groups }
                .toSet()
                .forEach {
                    assertGroupExistsOnCourse(it, courseId)
                    assertTeacherOrAdminHasAccessToCourseGroup(caller, courseId, it)
                }

        return insertStudentCourseAccesses(courseId, students)
    }
}

private data class StudentWithAccount(val id: String, val email: String, val groups: Set<Long>)
private data class StudentNoAccount(val email: String, val groups: Set<Long>)

private fun insertStudentCourseAccesses(courseId: Long, students: List<StudentNoAccount>):
        AddStudentsToCourseController.Resp {

    val now = DateTime.now()
    val courseEntity = EntityID(courseId, Course)

    return transaction {
        val studentsWithAccount = mutableSetOf<StudentWithAccount>()
        val studentsNoAccount = mutableSetOf<StudentNoAccount>()

        students.forEach {
            val studentId = (Student innerJoin Account)
                    .slice(Student.id)
                    .select { Account.email.lowerCase() eq it.email }
                    .map { it[Student.id].value }
                    .singleOrNull()

            if (studentId != null) {
                studentsWithAccount.add(StudentWithAccount(studentId, it.email, it.groups))
            } else {
                studentsNoAccount.add(StudentNoAccount(it.email, it.groups))
            }
        }

        val newStudentsWithAccount = studentsWithAccount.filter {
            StudentCourseAccess.select {
                StudentCourseAccess.student eq it.id and (StudentCourseAccess.course eq courseId)
            }.count() == 0
        }

        log.debug { "Granting access to students (the rest already have access): $newStudentsWithAccount" }
        log.debug { "Granting pending access to students: $studentsNoAccount" }

        newStudentsWithAccount.forEach { newStudent ->
            val accessId = StudentCourseAccess.insertAndGetId {
                it[course] = courseEntity
                it[student] = EntityID(newStudent.id, Student)
            }
            StudentGroupAccess.batchInsert(newStudent.groups) {
                this[StudentGroupAccess.student] = newStudent.id
                this[StudentGroupAccess.course] = courseId
                this[StudentGroupAccess.group] = EntityID(it, Group)
                this[StudentGroupAccess.courseAccess] = accessId
            }
        }

        // Delete & create pending accesses to update validFrom & groups
        StudentPendingAccess.deleteWhere {
            StudentPendingAccess.course eq courseId and
                    (StudentPendingAccess.email inList studentsNoAccount.map { it.email })
        }
        studentsNoAccount.forEach { pendingStudent ->
            val accessId = StudentPendingAccess.insertAndGetId {
                it[course] = courseEntity
                it[email] = pendingStudent.email
                it[validFrom] = now
            }
            StudentPendingGroup.batchInsert(pendingStudent.groups) {
                this[StudentPendingGroup.email] = pendingStudent.email
                this[StudentPendingGroup.course] = courseId
                this[StudentPendingGroup.group] = EntityID(it, Group)
                this[StudentPendingGroup.pendingAccess] = accessId
            }
        }

        AddStudentsToCourseController.Resp(newStudentsWithAccount.size, studentsNoAccount.size)
    }
}
