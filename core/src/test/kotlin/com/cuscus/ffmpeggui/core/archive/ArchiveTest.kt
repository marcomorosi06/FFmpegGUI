package com.cuscus.ffmpeggui.core.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

class ArchiveTest {

    /** A tar (GNU, with a folder and a long name) compressed with bzip2 -9, made with Python's tarfile and bz2. */
    private val archive: ByteArray = Base64.getDecoder().decode(
        "QlpoOTFBWSZTWevlTkEAB4//////////////////////////////////////////////0AN+AAAAEmAAmAAJgAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAkwAEwABMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEmAAmAAJgAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAkwAEwABMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAApVJFT2KZqm0anijR6BNNqM0Rsgm0y" +
        "aTRp6mgwmmj1D0hphqMnlPSHo0nqPQgDEDQAaeoxGgMQA0GQGgBoGgDTCADTQ2oZGnb7DW4tHpOJ4HFt2hMxr8I2WZJdyMNN" +
        "p9NhibfhgM9l5enxjFZMW1wHdoxozsYMmMMfPRn4xp3eMqO8RjbhGFJGejvMd6jQRuMblG5xukbrHe43aO+Ru8aGO+x36N4j" +
        "eY0Ub1G9xvBzB4CNyakpGDHjRxpI32N+jf44CPAxwMaWOCjTRp42mojT67mdh4OmFnGl3smIpGAnmuO1nH7gHD4WXGRlY3D6" +
        "jHYsKYsUcFjcLhhHgsiww4W1GPhkxm+6ZGGawjFk5mwjFj4ZUYo8PHiI8THio4Okja8THEFI4qNFHFxn/FxxkeMjjY8bHjo1" +
        "UePjyEeRjVxoOOjQ5erjyUeTjdPKRvMcfGN5WNFHIRyMclHJxrIyHlo0keXjlI5WPMRnQtbGSa6NbrjzMcHvRSNfHLRsTY+c" +
        "lo+GjMothGbjl42zduYjMakpGa2MbYZBSMzGdRvzCVnMmNToNKy8xmkWLORmsDP5WbwCzcZEczGWedjH2Rq42fEGcwjQbid1" +
        "jIjz8Ytls4yk5pNnHNxzkegj0Meij0cekj0semj08c7HPRz8eoj1Meqj1cesjoI9bHro9fHsI9jHso9nHtI9rHto6GPbxtI8" +
        "J7iOXj3MdFHRx0ke6j3cdLHvI97Hvo9/HwI+DHTR08dRHwo+HHxI+LHxo+PHyI+THyo6mPlx8yPmx86Oqjq4+fH0I+jH0o+n" +
        "H1I6yPqx9aPrx9iPsx9qPtx9yPux1sfej78fgj8Mfijro/HH5I/LH5o/PH6I/TH6o6+P1x2EdjHZR2cfsj9sfuj98fwj+Mfy" +
        "j+cf0j+sdpH9o7WP7x/iP8x20aq2sYdBHbR/qP9x/yO3jMF3EdxH/Y/9Hcx/8XckU4UJDr5U5BA=",
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `files come out of a tar bz2 whole`() {
        val tar = TarReader(Bzip2InputStream(ByteArrayInputStream(archive)))
        val first = tar.next()!!
        assertEquals("model/tokens.txt", first.path)
        assertEquals("tokens.txt", first.name)
        assertEquals("f41b8c2b3930299ac5e49815e528621f3a7c7f4b8a639a2882b671f644381b45", sha256(tar.content.readBytes()))
        val second = tar.next()!!
        assertEquals("long_".repeat(30) + "name.bin", second.name)
        assertEquals(2_307L, second.size)
        assertEquals("10a3724a2016cecfe5726500dbb306f10ecac3d6b828a7854f9696b09e38f871", sha256(tar.content.readBytes()))
        assertNull(tar.next())
    }

    @Test
    fun `a skipped file does not disturb the next`() {
        val tar = TarReader(Bzip2InputStream(ByteArrayInputStream(archive)))
        tar.next()
        assertTrue(tar.next()!!.name.endsWith("name.bin"))
    }

    @Test(expected = IOException::class)
    fun `damaged data is refused`() {
        val damaged = archive.copyOf()
        damaged[damaged.size / 2] = (damaged[damaged.size / 2].toInt() xor 0x55).toByte()
        val stream = Bzip2InputStream(ByteArrayInputStream(damaged))
        val tar = TarReader(stream)
        while (tar.next() != null) tar.content.readBytes()
        // The tar may end early on garbage: reading the rest checks the CRCs.
        stream.readBytes()
    }

    @Test(expected = IOException::class)
    fun `not bzip2 at all`() {
        Bzip2InputStream(ByteArrayInputStream("PK not this".toByteArray()))
    }
}
