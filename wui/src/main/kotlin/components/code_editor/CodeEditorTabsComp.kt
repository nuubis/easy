package components.code_editor

import rip.kspar.ezspa.Component
import rip.kspar.ezspa.doInPromise
import template

class CodeEditorTabsComp(

    parent: Component?,
) : Component(parent) {

    override val children: List<Component>
        get() = listOf()

    override fun create() = doInPromise {

    }

    override fun render() = template(
        """
            
        """.trimIndent(),

        )
}