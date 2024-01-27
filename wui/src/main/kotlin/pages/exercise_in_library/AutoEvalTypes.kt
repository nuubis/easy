package pages.exercise_in_library

import translation.Str

object AutoEvalTypes {

    data class TypeTemplate(
        val name: String, val container: String,
        val allowedTime: Int, val allowedMemory: Int,
        val editor: TypeEditor,
        val helpTextHtml: String,
        val evaluateScript: String,
        val assets: Map<String, String> = emptyMap(),
    )

    enum class TypeEditor {
        TSL_COMPOSE, TSL_YAML,
        CODE_EDITOR
    }

    const val TSL_CONTAINER = "tiivad:tsl-compose"

    val templates = listOf(
        TypeTemplate(
            "TSL", TSL_CONTAINER,
            7, 30, TypeEditor.TSL_COMPOSE,
            "",
            """
                cd student-submission
                python generated_0.py
            """.trimIndent(),
            mapOf("generated_0.py" to "")
        ),

        TypeTemplate(
            "Silmused PostgreSQL", "silmused",
            30, 50, TypeEditor.CODE_EDITOR,
            "",
            """
                cd student-submission
                mv lahendus.py lahendus.sql
                
                service postgresql start >/dev/null 2>&1
                silmused lahendus.sql tests.py postgres localhost 5433 postgres
            """.trimIndent(),
            mapOf(
                "tests.py" to """
                from silmused.TitleLayer import TitleLayer
                from silmused.ChecksLayer import ChecksLayer
                from silmused.ExecuteLayer import ExecuteLayer
                from silmused.tests.DataTest import DataTest
                from silmused.tests.StructureTest import StructureTest
                from silmused.tests.ConstraintTest import ConstraintTest
                from silmused.tests.FunctionTest import FunctionTest
                from silmused.tests.IndexTest import IndexTest
                from silmused.tests.ProcedureTest import ProcedureTest
                from silmused.tests.TriggerTest import TriggerTest
                from silmused.tests.ViewTest import ViewTest

                tests = [
                    
                ]
            """.trimIndent()
            )
        ),

        TypeTemplate(
            "TSL YAML", "tiivad:tsl-spec",
            7, 30, TypeEditor.TSL_YAML,
            "TODO TSL YAML",
            """
                cd student-submission
                python generated_0.py
            """.trimIndent(),
        ),

        TypeTemplate(
            "Python Grader", "pygrader",
            7, 30, TypeEditor.CODE_EDITOR,
            "Me ei soovita uusi automaatkontrolle Python Graderiga koostada, aga teegi leiab siit: https://github.com/kspar/python-grader",
            """
                cd student-submission
                python -m grader.easy
            """.trimIndent(),
            mapOf(
                "tester.py" to """
                        from grader import *
                        from grader.utils import *
                        
                        @test
                        @expose_ast
                        @set_description("Test 1")
                        def test1(m, AST):
                            pass
                    """.trimIndent()
            )
        ),

        TypeTemplate(
            Str.autoAssessTypeImgRec, "imgrec",
            20, 50, TypeEditor.CODE_EDITOR,
            "TODO imgrec",
            """
                cd student-submission
                python3 kontroll.py
                xvfb-run python3 modified_student_submission.py
    
                python3 -m grader.easy --assets screenshot.jpg tester_2.py --no-solution-file
            """.trimIndent()
        )
    )

}