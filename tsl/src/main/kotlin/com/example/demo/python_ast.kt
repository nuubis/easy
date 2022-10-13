package com.example.demo


abstract class PyASTElement {
    abstract fun generatePyString(): String
}

abstract class PyASTPrimitive : PyASTElement()


class PyFunctionCall(val name: String, val namedArgs: Map<String, PyASTPrimitive?>) : PyASTPrimitive() {
    override fun generatePyString(): String {
        val argsString = namedArgs.map {
            "${it.key.trim()}=${it.value?.generatePyString()}"
        }.joinToString(", ")

        return "$name($argsString)\n"
    }
}

// TODO: .generatePyString() võiks toimuda hiljem, mitte igas harus

class PyInt(val value: Long) : PyASTPrimitive() {
    // Long.toString() is always a valid Python integer primitive?
    override fun generatePyString() = value.toString()
}

class PyStr(val value: String?) : PyASTPrimitive() {
    override fun generatePyString() = "'''${value?.replace("'''", "\\'''")?.trim()}'''"
}

class PyFloat(val value: Double) : PyASTPrimitive() {
    override fun generatePyString() = TODO("Not yet implemented")
}

class PyList(val values: List<PyASTPrimitive>) : PyASTPrimitive() {
    override fun generatePyString() = values.joinToString(", ", "[", "]") {
        it.generatePyString()
    }
}

open class PyTuple(val values: List<PyASTPrimitive>) : PyASTPrimitive() {
    override fun generatePyString() = values.joinToString(", ", "(", ")") {
        it.generatePyString()
    }
}

class PyPair(value1: PyASTPrimitive, value2: PyASTPrimitive) : PyTuple(listOf(value1, value2))

class PyBool(val value: Boolean?) : PyASTPrimitive() {
    override fun generatePyString() = value.toString().replaceFirstChar { it.uppercase() }
}