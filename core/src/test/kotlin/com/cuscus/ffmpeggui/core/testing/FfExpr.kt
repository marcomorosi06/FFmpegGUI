package com.cuscus.ffmpeggui.core.testing

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * A small evaluator of the FFmpeg expressions the builders write: numbers
 * (with exponents), the variables in [variables], + - * /, and the functions
 * if, lt, lte, gt, gte, clip, min, max, pow, exp, log, sin, st and ld (with
 * FFmpeg's 10 variable slots, evaluated left to right as FFmpeg does).
 */
class FfExpr(private val text: String, private val variables: Map<String, Double>) {
    private var pos = 0
    private val slots = DoubleArray(10)

    constructor(text: String, t: Double) : this(text, mapOf("t" to t, "T" to t))

    fun value(): Double = sum().also { require(pos == text.length) { "Unparsed: ${text.substring(pos)}" } }

    private fun sum(): Double {
        var v = product()
        while (pos < text.length && (text[pos] == '+' || text[pos] == '-')) {
            val op = text[pos++]
            val r = product()
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun product(): Double {
        var v = unary()
        while (pos < text.length && (text[pos] == '*' || text[pos] == '/')) {
            val op = text[pos++]
            val r = unary()
            v = if (op == '*') v * r else v / r
        }
        return v
    }

    private fun unary(): Double = if (text[pos] == '-') { pos++; -unary() } else atom()

    private fun atom(): Double {
        if (text[pos] == '(') {
            pos++
            val v = sum()
            expect(')')
            return v
        }
        if (text[pos].isDigit() || text[pos] == '.') {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E') && pos + 1 < text.length &&
                (text[pos + 1].isDigit() || text[pos + 1] == '-' || text[pos + 1] == '+')
            ) {
                pos += 2
                while (pos < text.length && text[pos].isDigit()) pos++
            }
            return text.substring(start, pos).toDouble()
        }
        val start = pos
        while (pos < text.length && (text[pos].isLetter() || text[pos] == '_')) pos++
        val name = text.substring(start, pos)
        if (pos >= text.length || text[pos] != '(') {
            return variables[name] ?: error("Unknown variable '$name' at $start in $text")
        }
        pos++ // '('
        // if() only evaluates the branch it takes, as FFmpeg does (st() in the other has no effect).
        if (name == "if") {
            val condition = sum()
            expect(',')
            return if (condition != 0.0) {
                val v = sum()
                expect(',')
                skipArgument()
                expect(')')
                v
            } else {
                skipArgument()
                expect(',')
                val v = sum()
                expect(')')
                v
            }
        }
        val args = mutableListOf(sum())
        while (text[pos] == ',') {
            pos++
            args += sum()
        }
        expect(')')
        return when (name) {
            "lt" -> if (args[0] < args[1]) 1.0 else 0.0
            "lte" -> if (args[0] <= args[1]) 1.0 else 0.0
            "gt" -> if (args[0] > args[1]) 1.0 else 0.0
            "gte" -> if (args[0] >= args[1]) 1.0 else 0.0
            "clip" -> args[0].coerceIn(args[1], args[2])
            "min" -> minOf(args[0], args[1])
            "max" -> maxOf(args[0], args[1])
            "pow" -> args[0].pow(args[1])
            "exp" -> exp(args[0])
            "log" -> ln(args[0])
            "sin" -> sin(args[0])
            "st" -> args[1].also { slots[args[0].toInt()] = it }
            "ld" -> slots[args[0].toInt()]
            else -> error("Unknown function $name")
        }
    }

    private fun expect(c: Char) {
        require(pos < text.length && text[pos] == c) { "Expected '$c' at $pos in $text" }
        pos++
    }

    /** Moves past one argument without evaluating it. */
    private fun skipArgument() {
        var depth = 0
        while (pos < text.length) {
            val c = text[pos]
            if (depth == 0 && (c == ',' || c == ')')) return
            if (c == '(') depth++
            if (c == ')') depth--
            pos++
        }
    }
}
