package com.cuscus.ffmpeggui.conversion

/**
 * Thread-safe, bounded log store. FFmpeg can emit thousands of lines; keeping
 * only some of them bounds both memory and the cost of each UI update.
 *
 * What is kept is what tells what happened: the first [headCapacity] lines (the
 * command, the files read, how their streams are mapped: what explains a run
 * that goes wrong from the start) and the latest [tailCapacity] lines. FFmpeg's
 * progress lines ("frame= ... time= ...") replace one another instead of
 * piling up, so that a run that stalls does not push everything else out.
 */
internal class LogBuffer(
    private val headCapacity: Int = DEFAULT_HEAD,
    private val tailCapacity: Int = DEFAULT_TAIL,
) {

    private val head = ArrayList<String>()
    private val tail = ArrayDeque<String>()
    private var dropped = 0
    private var lastWasProgress = false

    @Synchronized
    fun add(line: String) {
        val progress = isProgress(line)
        if (progress && lastWasProgress) {
            // The newest progress takes the place of the one before it.
            if (tail.isNotEmpty()) tail.removeLast() else if (head.isNotEmpty()) head.removeAt(head.lastIndex)
        }
        lastWasProgress = progress
        if (head.size < headCapacity && tail.isEmpty()) {
            head += line
            return
        }
        if (tail.size == tailCapacity) {
            tail.removeFirst()
            dropped++
        }
        tail.addLast(line)
    }

    @Synchronized
    fun snapshot(): List<String> = buildList(head.size + tail.size + 1) {
        addAll(head)
        if (dropped > 0) add("[... $dropped lines ...]")
        addAll(tail)
    }

    private fun isProgress(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("frame=") || trimmed.startsWith("size=")
    }

    private companion object {
        const val DEFAULT_HEAD = 200
        const val DEFAULT_TAIL = 300
    }
}
