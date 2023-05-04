package core.ems.service

import core.db.*
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.FileSystemResourceAccessor
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.sql.DataSource


class CoursesKtTest {
    private val embeddedPostgres: EmbeddedPostgres = EmbeddedPostgres.start()
    private val dataSource: DataSource = embeddedPostgres.postgresDatabase

    private val teacher1Id = "teacher1"
    private val student1Id = "student1"
    private val student2Id = "student2"
    private val studentIds = listOf(student1Id, student2Id)


    private val course1Id = 1L

    private val exercise1Id = 1L
    private val exercise2Id = exercise1Id + 1

    private val exerciseVer1Id = 1L
    private val exerciseVer2Id = exerciseVer1Id + 1

    private val ce1Id = 1L
    private val ce2Id = ce1Id + 1


    @Test
    fun `getCourse should return correct course when course exists`() {
        val course = getCourse(course1Id)
        assertEquals("Test Course", course?.title)
        assertEquals("TC", course?.alias)
    }

    @Test
    fun `selectAllCourseExercisesLatestSubmissions should return 2 exercises`() {
        val latestSubmissions: List<ExercisesResp> = selectAllCourseExercisesLatestSubmissions(course1Id)
        assertEquals(setOf(exerciseVer1Id, exerciseVer2Id), latestSubmissions.map { it.exerciseId.toLong() }.toSet())
    }

    @Test
    fun `selectAllCourseExercisesLatestSubmissions should return 4 submissions (1 null, 3 graded)`() {
        val latestSubmissions: List<ExercisesResp> = selectAllCourseExercisesLatestSubmissions(course1Id)
        val submissions = latestSubmissions.map { it.latestSubmissions }.flatten().map { it.latestSubmission }
        assertEquals(4, submissions.size)
    }


    /**
     * Student 1 has two EX1 submissions:
     * 1. grade: 71 - expect not see it
     * 2. grade: 81 - expect to see only this
     *
     * Student 1 has two EX2 submissions:
     * 1. grade: 91 - expect not see
     * 2. grade: 99 - expect to see only this
     */
    @Test
    fun `selectAllCourseExercises student 1 should return 2 submissions with grades 81 and 99`() {
        val latestSubmissions: List<ExercisesResp> = selectAllCourseExercisesLatestSubmissions(course1Id)

        val ex1Submissions = latestSubmissions.single { it.exerciseId.toLong() == ce1Id }
        val ex2Submissions = latestSubmissions.single { it.exerciseId.toLong() == ce2Id }

        val stud1Ex1Sub = ex1Submissions.latestSubmissions.single { it.accountId == student1Id }
        assertEquals(81, stud1Ex1Sub.latestSubmission!!.grade!!.grade)
        assertEquals(false, stud1Ex1Sub.latestSubmission!!.grade!!.isAutograde)

        val stud1Ex2Sub = ex2Submissions.latestSubmissions.single { it.accountId == student1Id }
        assertEquals(99, stud1Ex2Sub.latestSubmission!!.grade!!.grade)
        assertEquals(false, stud1Ex2Sub.latestSubmission!!.grade!!.isAutograde)
    }


    /**
     * Student 2 has 0 EX1 submissions.
     * 1. expect to see null.
     *
     * Student 2 has 1 EX2 submissions:
     * 1. grade: 51 - expect to see
     */
    @Test
    fun `selectAllCourseExercises student 2 should return 1 submission with grade 51 and 1 "null" submission`() {
        val latestSubmissions: List<ExercisesResp> = selectAllCourseExercisesLatestSubmissions(course1Id)

        val ex1Submissions = latestSubmissions.single { it.exerciseId.toLong() == ce1Id }
        val ex2Submissions = latestSubmissions.single { it.exerciseId.toLong() == ce2Id }

        val stud1Ex1Sub = ex1Submissions.latestSubmissions.single { it.accountId == student2Id }
        assertNull(stud1Ex1Sub.latestSubmission)

        val stud1Ex2Sub = ex2Submissions.latestSubmissions.single { it.accountId == student2Id }
        assertEquals(51, stud1Ex2Sub.latestSubmission!!.grade!!.grade)
        assertEquals(false, stud1Ex2Sub.latestSubmission!!.grade!!.isAutograde)
    }

    /**
     * EX threshold: 90
     * EX1 submissions:
     * 1. grade 81 (started)
     * 2. no submission (unstarted)
     *
     * Threshold: 80
     * EX2 submissions:
     * 1. grade: 99 (completed)
     * 2. grade: 51 (started)
     */
    @Test
    fun `selectAllCourseExercises check completedCount, startedCount, unstartedCount, ungradedCount`() {
        val latestSubmissions: List<ExercisesResp> = selectAllCourseExercisesLatestSubmissions(course1Id)

        val ex1Submissions = latestSubmissions.single { it.exerciseId.toLong() == ce1Id }
        val ex2Submissions = latestSubmissions.single { it.exerciseId.toLong() == ce2Id }

        assertEquals(0, ex1Submissions.completedCount)
        assertEquals(1, ex1Submissions.startedCount)
        assertEquals(1, ex1Submissions.unstartedCount)
        assertEquals(0, ex1Submissions.ungradedCount)

        assertEquals(1, ex2Submissions.completedCount)
        assertEquals(1, ex2Submissions.startedCount)
        assertEquals(0, ex2Submissions.unstartedCount)
        assertEquals(0, ex2Submissions.ungradedCount)
    }


    @Test
    fun `selectStudentsOnCourse should return two students`() {
        val students = selectStudentsOnCourse(course1Id).map { it.id }
        assertEquals(students.size, 2)
        assertEquals(students.toSet(), studentIds.toSet())
    }

    @Test
    fun `getCourse should return null when course does not exist`() {
        val course = getCourse(course1Id + 1)
        assertNull(course)
    }

    @BeforeEach
    fun bootstrap() {
        Database.connect(dataSource)

        Liquibase(
            "db/changelog.xml",
            FileSystemResourceAccessor(),
            JdbcConnection(dataSource.connection)
        ).update("development")

        transaction {
            addLogger(StdOutSqlLogger)

            Account.insert {
                it[id] = EntityID(student1Id, Account)
                it[email] = "user1@example.com"
                it[givenName] = "John"
                it[familyName] = "Doe"
                it[createdAt] = DateTime.parse("2023-04-28T12:00:00Z")
                it[lastSeen] = DateTime.parse("2023-04-28T12:00:00Z")
                it[idMigrationDone] = true
            }

            Account.insert {
                it[id] = EntityID(student2Id, Account)
                it[email] = "user2@example.com"
                it[givenName] = "Jane"
                it[familyName] = "Doe"
                it[createdAt] = DateTime.parse("2023-04-27T12:00:00Z")
                it[lastSeen] = DateTime.parse("2023-04-27T12:00:00Z")
                it[idMigrationDone] = true
            }
            Account.insert {
                it[id] = EntityID(teacher1Id, Account)
                it[email] = "user3@example.com"
                it[givenName] = "Bob"
                it[familyName] = "Smith"
                it[createdAt] = DateTime.parse("2023-04-26T12:00:00Z")
                it[lastSeen] = DateTime.parse("2023-04-26T12:00:00Z")
                it[idMigrationDone] = true
            }

            Student.insert {
                it[id] = student1Id
                it[createdAt] = DateTime.parse("2023-04-28T12:00:00Z")
            }
            Student.insert {
                it[id] = student2Id
                it[createdAt] = DateTime.parse("2023-04-27T12:00:00Z")
            }

            Teacher.insert {
                it[id] = teacher1Id
                it[createdAt] = DateTime.parse("2023-04-26T12:00:00Z")
            }

            Dir.insert {
                it[id] = 1L
                it[name] = "1"
                it[isImplicit] = true
                it[createdAt] = DateTime.now()
                it[modifiedAt] = DateTime.now()
            }

            Exercise.insert {
                it[id] = exercise1Id
                it[owner] = EntityID(teacher1Id, Account)
                it[createdAt] = DateTime.now()
                it[public] = true
                it[anonymousAutoassessEnabled] = false
                it[successfulAnonymousSubmissionCount] = 0
                it[unsuccessfulAnonymousSubmissionCount] = 0
                it[removedSubmissionsCount] = 0
                it[dir] = EntityID(1L, Dir)
            }

            Exercise.insert {
                it[id] = exercise2Id
                it[owner] = EntityID(teacher1Id, Account)
                it[createdAt] = DateTime.now()
                it[public] = true
                it[anonymousAutoassessEnabled] = false
                it[successfulAnonymousSubmissionCount] = 0
                it[unsuccessfulAnonymousSubmissionCount] = 0
                it[removedSubmissionsCount] = 0
                it[dir] = EntityID(1L, Dir)
            }


            ExerciseVer.insert {
                it[id] = exerciseVer1Id
                it[exercise] = EntityID(exercise1Id, Exercise)
                it[author] = EntityID(teacher1Id, Account)
                it[validFrom] = DateTime.now().minusDays(1)
                it[graderType] = GraderType.TEACHER
                it[title] = "Exercise 1"
                it[textHtml] = "<p>Exercise 1 description</p>"
                it[textAdoc] = "Exercise 1 description"
            }

            ExerciseVer.insert {
                it[id] = exerciseVer2Id
                it[exercise] = EntityID(exercise2Id, Exercise)
                it[author] = EntityID(teacher1Id, Account)
                it[validFrom] = DateTime.now().minusDays(1)
                it[graderType] = GraderType.TEACHER
                it[title] = "Exercise 2"
                it[textHtml] = "<p>Exercise 2 description</p>"
                it[textAdoc] = "Exercise 2 description"
            }

            Course.insert {
                it[id] = course1Id
                it[title] = "Test Course"
                it[alias] = "TC"
                it[createdAt] = DateTime.now()
                it[moodleShortName] = "TCSN"
                it[moodleSyncStudents] = false
                it[moodleSyncGrades] = false
                it[moodleSyncStudentsInProgress] = false
                it[moodleSyncGradesInProgress] = false
            }

            CourseExercise.insert {
                it[id] = ce1Id
                it[course] = course1Id
                it[exercise] = exercise1Id
                it[createdAt] = DateTime.now()
                it[modifiedAt] = DateTime.now()
                it[gradeThreshold] = 90
                it[studentVisibleFrom] = DateTime.now()
                it[softDeadline] = DateTime.now().plusDays(7)
                it[hardDeadline] = DateTime.now().plusDays(14)
                it[orderIdx] = 1
                it[assessmentsStudentVisible] = true
                it[instructionsHtml] = "<p>Course exercise instructions</p>"
                it[instructionsAdoc] = "Course exercise instructions"
                it[titleAlias] = "Course exercise title alias"
            }

            CourseExercise.insert {
                it[id] = ce2Id
                it[course] = course1Id
                it[exercise] = exercise2Id
                it[createdAt] = DateTime.now()
                it[modifiedAt] = DateTime.now()
                it[gradeThreshold] = 80
                it[studentVisibleFrom] = DateTime.now()
                it[softDeadline] = DateTime.now().plusDays(7)
                it[hardDeadline] = DateTime.now().plusDays(14)
                it[orderIdx] = 1
                it[assessmentsStudentVisible] = true
                it[instructionsHtml] = "<p>Course exercise instructions</p>"
                it[instructionsAdoc] = "Course exercise instructions"
                it[titleAlias] = "Course exercise title alias"
            }

            TeacherCourseAccess.insert {
                it[teacher] = teacher1Id
                it[course] = course1Id
                it[createdAt] = DateTime.now()
            }

            studentIds.forEach { id ->
                StudentCourseAccess.insert {
                    it[student] = id
                    it[course] = course1Id
                    it[createdAt] = DateTime.now()
                }
            }


            // Stud 1: EX1 2 x submissions + grade, EX2: 2 x submission + grade
            Submission.insert {
                it[courseExercise] = ce1Id
                it[student] = student1Id
                it[createdAt] = DateTime.now()
                it[solution] = "submission"
                it[autoGradeStatus] = AutoGradeStatus.NONE
                it[grade] = 71
                it[isAutoGrade] = false
            }

            Submission.insert {
                it[courseExercise] = ce1Id
                it[student] = student1Id
                it[createdAt] = DateTime.now()
                it[solution] = "submission"
                it[autoGradeStatus] = AutoGradeStatus.NONE
                it[grade] = 81
                it[isAutoGrade] = false
            }

            Submission.insert {
                it[courseExercise] = ce2Id
                it[student] = student1Id
                it[createdAt] = DateTime.now()
                it[solution] = "submission"
                it[autoGradeStatus] = AutoGradeStatus.NONE
                it[grade] = 91
                it[isAutoGrade] = false
            }

            Submission.insert {
                it[courseExercise] = ce2Id
                it[student] = student1Id
                it[createdAt] = DateTime.now()
                it[solution] = "submission"
                it[autoGradeStatus] = AutoGradeStatus.NONE
                it[grade] = 99
                it[isAutoGrade] = false
            }

            // Stud 2: EX1: no submission, no grade, EX2: submission, grade
            Submission.insert {
                it[courseExercise] = ce2Id
                it[student] = student2Id
                it[createdAt] = DateTime.now()
                it[solution] = "submission"
                it[autoGradeStatus] = AutoGradeStatus.NONE
                it[grade] = 51
                it[isAutoGrade] = false
            }
        }
    }

    @AfterEach
    fun shutdown() {
        embeddedPostgres.close()
    }
}

