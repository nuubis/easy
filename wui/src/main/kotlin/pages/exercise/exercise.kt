package pages.exercise

import DateSerializer
import Icons
import Str
import components.BreadcrumbsComp
import components.EditModeButtonsComp
import components.PageTabsComp
import dao.ExerciseDAO
import dao.LibraryDirDAO
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import pages.Title
import pages.exercise_library.createDirChainCrumbs
import pages.exercise_library.createPathChainSuffix
import pages.sidenav.Sidenav
import queries.ReqMethod
import queries.fetchEms
import queries.http200
import queries.parseTo
import rip.kspar.ezspa.Component
import rip.kspar.ezspa.IdGenerator
import rip.kspar.ezspa.doInPromise
import successMessage
import tmRender
import kotlin.js.Date


@Serializable
data class ExerciseDTO(
    var is_public: Boolean,
    var grader_type: GraderType,
    var title: String,
    var text_adoc: String? = null,
    var grading_script: String? = null,
    var container_image: String? = null,
    var max_time_sec: Int? = null,
    var max_mem_mb: Int? = null,
    var assets: List<AssetDTO>? = null,
    var executors: List<ExecutorDTO>? = null,
    val dir_id: String,
    @Serializable(with = DateSerializer::class)
    val created_at: Date,
    val owner_id: String,
    @Serializable(with = DateSerializer::class)
    val last_modified: Date,
    val last_modified_by_id: String,
    val text_html: String? = null,
    val on_courses: List<OnCourseDTO>,
)

@Serializable
data class AssetDTO(
    val file_name: String,
    val file_content: String
)

@Serializable
data class ExecutorDTO(
    val id: String,
    val name: String
)

@Serializable
data class OnCourseDTO(
    val id: String,
    val title: String,
    val course_exercise_id: String,
    val course_exercise_title_alias: String?
)

enum class GraderType {
    AUTO, TEACHER
}


class ExerciseRootComp(
    private val exerciseId: String,
    private val setPathSuffix: (String) -> Unit,
    dstId: String
) : Component(null, dstId) {

    private val exerciseTabId = IdGenerator.nextId()
    private val autoassessTabId = IdGenerator.nextId()
    private val testingTabId = IdGenerator.nextId()

    private lateinit var crumbs: BreadcrumbsComp
    private lateinit var tabs: PageTabsComp
    private lateinit var editModeBtns: EditModeButtonsComp
    private lateinit var addToCourseModal: AddToCourseModalComp

    private lateinit var exerciseTab: ExerciseTabComp
    private lateinit var autoassessTab: AutoAssessmentTabComp

    override val children: List<Component>
        get() = listOf(crumbs, tabs, addToCourseModal)

    override fun create() = doInPromise {
        val exercise = fetchEms("/exercises/$exerciseId", ReqMethod.GET,
            successChecker = { http200 }).await()
            .parseTo(ExerciseDTO.serializer()).await()

        val parents = LibraryDirDAO.getDirParents(exercise.dir_id).await().reversed()

        setPathSuffix(createPathChainSuffix(parents.map { it.name } + exercise.title))

        crumbs = BreadcrumbsComp(createDirChainCrumbs(parents, exercise.title), this)

        // TODO: wrong parent
        editModeBtns = EditModeButtonsComp(::editModeChanged, ::saveExercise, ::wishesToCancel, parent = this)

        tabs = PageTabsComp(
            buildList {
                add(
                    PageTabsComp.Tab("Ülesanne", preselected = true, id = exerciseTabId) {
                        ExerciseTabComp(exercise, ::validChanged, it)
                            .also { exerciseTab = it }
                    }
                )

                add(
                    PageTabsComp.Tab("Automaatkontroll", id = autoassessTabId) {
                        val aaProps = if (exercise.grading_script != null) {
                            AutoAssessmentTabComp.AutoAssessProps(
                                exercise.grading_script!!,
                                exercise.assets!!.associate { it.file_name to it.file_content },
                                exercise.container_image!!, exercise.max_time_sec!!, exercise.max_mem_mb!!
                            )
                        } else null

                        AutoAssessmentTabComp(aaProps, ::validChanged, it)
                            .also { autoassessTab = it }
                    }
                )

                if (exercise.grader_type == GraderType.AUTO) {
                    add(
                        PageTabsComp.Tab("Katsetamine", id = testingTabId) {
                            TestingTabComp(exerciseId, it)
                        }
                    )
                }
            },
            trailerComp = editModeBtns,
            parent = this
        )
        addToCourseModal = AddToCourseModalComp(exerciseId, exercise.title, this)

        Title.update {
            it.pageTitle = exercise.title
            it.parentPageTitle = Str.exerciseLibrary()
        }

        Sidenav.replacePageSection(
            Sidenav.PageSection(
                exercise.title, listOf(
                    Sidenav.Action(Icons.add, "Lisa kursusele") {
                        val r = addToCourseModal.openWithClosePromise().await()
                        if (r != null) {
                            recreate()
                        }
                    }
                )
            )
        )
    }

    override fun hasUnsavedChanges(): Boolean =
        exerciseTab.hasUnsavedChanges() ||
                autoassessTab.hasUnsavedChanges()

    override fun render(): String = tmRender(
        "t-c-exercise",
        "crumbsDstId" to crumbs.dstId,
        "tabsDstId" to tabs.dstId,
        "addToCourseModalDstId" to addToCourseModal.dstId,
    )

    private suspend fun recreate() {
        val selectedTab = tabs.getSelectedTab()
        val editorView = autoassessTab.getEditorActiveView()

        createAndBuild().await()

        tabs.setSelectedTab(selectedTab)
        autoassessTab.setEditorActiveView(editorView)
    }

    private fun validChanged(_notUsed: Boolean) {
        editModeBtns.setSaveEnabled(exerciseTab.isValid() && autoassessTab.isValid())
    }

    private suspend fun editModeChanged(nowEditing: Boolean) {
        tabs.refreshIndicator()

        // exercise tab: title, text editor
        exerciseTab.setEditable(nowEditing)

        // aa tab: attrs, editor
        val editorView = autoassessTab.getEditorActiveView()
        autoassessTab.setEditable(nowEditing)
        autoassessTab.setEditorActiveView(editorView)
    }

    private suspend fun saveExercise(): Boolean {
        val exerciseProps = exerciseTab.getEditedProps()
        val autoevalProps = autoassessTab.getEditedProps()

        ExerciseDAO.updateExercise(
            exerciseId,
            ExerciseDAO.UpdatedExercise(
                exerciseProps.title,
                exerciseProps.textAdoc,
                exerciseProps.textHtml,
                if (autoevalProps != null)
                    ExerciseDAO.Autoeval(
                        autoevalProps.containerImage,
                        autoevalProps.evalScript,
                        autoevalProps.assets,
                        autoevalProps.maxTime,
                        autoevalProps.maxMem,
                    )
                else null
            )
        ).await()

        successMessage { "Ülesanne salvestatud" }
        recreate()
        return true
    }

    private suspend fun wishesToCancel() =
        // TODO: modal
        if (hasUnsavedChanges())
            window.confirm("Siin lehel on salvestamata muudatusi. Kas oled kindel, et soovid muutmise lõpetada ilma salvestamata?")
        else true
}
