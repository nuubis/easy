package core.ems.service.exercise

import com.fasterxml.jackson.annotation.JsonProperty
import core.conf.security.EasyUser
import core.db.*
import core.ems.service.idToLongOrInvalidReq
import core.exception.InvalidRequestException
import mu.KotlinLogging
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.security.access.annotation.Secured
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("/v2")
class CreateExerciseCont {

    data class Req(
            @JsonProperty("title", required = true) val title: String,
            @JsonProperty("text_html", required = true) val textHtml: String,
            @JsonProperty("public", required = true) val public: Boolean,
            @JsonProperty("grader_type", required = true) val graderType: GraderType,
            @JsonProperty("grading_script", required = false) val gradingScript: String?,
            @JsonProperty("container_image", required = false) val containerImage: String?,
            @JsonProperty("max_time_sec", required = false) val maxTime: Int?,
            @JsonProperty("max_mem_mb", required = false) val maxMem: Int?,
            @JsonProperty("assets", required = false) val assets: List<ReqAsset>?,
            @JsonProperty("executors", required = false) val executors: List<ReqExecutor>?)

    data class ReqAsset(
            @JsonProperty("file_name", required = true) val fileName: String,
            @JsonProperty("file_content", required = true) val fileContent: String)

    data class ReqExecutor(
            @JsonProperty("executor_id", required = true) val executorId: String)

    data class Resp(
            @JsonProperty("id") val id: String)

    @Secured("ROLE_TEACHER", "ROLE_ADMIN")
    @PostMapping("/exercises")
    fun controller(@RequestBody dto: Req, caller: EasyUser): Resp {

        log.debug { "Create exercise '${dto.title}' by ${caller.id}" }
        return Resp(insertExercise(caller.id, dto).toString())
    }
}


private fun insertExercise(ownerId: String, req: CreateExerciseCont.Req): Long {
    val teacherId = EntityID(ownerId, Teacher)

    return transaction {
        addLogger(StdOutSqlLogger)

        var newAutoExerciseId: EntityID<Long>? = null

        if (req.graderType == GraderType.AUTO) {

            if (req.gradingScript == null ||
                    req.containerImage == null ||
                    req.maxTime == null ||
                    req.maxMem == null ||
                    req.assets == null ||
                    req.executors == null) {

                throw InvalidRequestException("Parameters for autoassessable exercise are missing.")
            }

            if (req.executors.isEmpty()) {
                throw InvalidRequestException("Autoassessable exercise must have at least 1 executor")
            }

            val executorIds = req.executors.map {
                val executorId = EntityID(it.executorId.idToLongOrInvalidReq(), Executor)
                if (Executor.select { Executor.id eq executorId }.count() == 0) {
                    throw InvalidRequestException("Executor $executorId does not exist")
                }
                executorId
            }

            newAutoExerciseId = AutoExercise.insertAndGetId {
                it[gradingScript] = req.gradingScript
                it[containerImage] = req.containerImage
                it[maxTime] = req.maxTime
                it[maxMem] = req.maxMem
            }

            Asset.batchInsert(req.assets) {
                this[Asset.autoExercise] = newAutoExerciseId
                this[Asset.fileName] = it.fileName
                this[Asset.fileContent] = it.fileContent
            }

            AutoExerciseExecutor.batchInsert(executorIds) {
                this[AutoExerciseExecutor.autoExercise] = newAutoExerciseId
                this[AutoExerciseExecutor.executor] = it
            }
        }


        val exerciseId = Exercise.insertAndGetId {
            it[owner] = teacherId
            it[public] = req.public
            it[createdAt] = DateTime.now()
        }

        ExerciseVer.insert {
            it[exercise] = exerciseId
            it[author] = teacherId
            it[validFrom] = DateTime.now()
            it[graderType] = req.graderType
            it[title] = req.title
            it[textHtml] = req.textHtml
            it[autoExerciseId] = newAutoExerciseId
        }

        exerciseId.value
    }
}
