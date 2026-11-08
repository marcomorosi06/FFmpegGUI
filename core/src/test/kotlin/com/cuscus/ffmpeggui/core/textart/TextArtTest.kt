package com.cuscus.ffmpeggui.core.textart

import com.cuscus.ffmpeggui.core.image.Raster
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextArtTest {

    // -----------------------------------------------------------------
    // Pictures and a font made up for the tests
    // -----------------------------------------------------------------

    private fun picture(w: Int, h: Int, color: (Int, Int) -> Int): Raster =
        Raster(w, h, IntArray(w * h) { color(it % w, it / w) })

    private fun opaque(rgb: Int) = (0xFF shl 24) or rgb
    private fun grey(v: Int) = opaque((v shl 16) or (v shl 8) or v)

    /** A 4 x 8 font: lines where they belong, the other characters of an even ink each. */
    private val atlas: GlyphAtlas = run {
        val w = 4
        val h = 8
        fun shape(f: (Int, Int) -> Boolean) = FloatArray(w * h) { if (f(it % w, it / w)) 1f else 0f }
        val maps = HashMap<Int, FloatArray>()
        for (code in Glyphs.candidates) {
            if (code == Glyphs.SPACE) continue
            maps[code] = when (code) {
                '-'.code -> shape { _, y -> y == 4 }
                '|'.code -> shape { x, _ -> x == 1 }
                '/'.code -> shape { x, y -> x == (7 - y) / 2 }
                '\\'.code -> shape { x, y -> x == y / 2 }
                '.'.code -> shape { x, y -> y == 7 && x == 1 }
                '@'.code -> FloatArray(w * h) { 0.9f }
                '#'.code -> FloatArray(w * h) { 0.7f }
                Glyphs.FULL_BLOCK -> FloatArray(w * h) { 1f }
                Glyphs.UPPER_HALF -> shape { _, y -> y < 4 }
                Glyphs.LOWER_HALF -> shape { _, y -> y >= 4 }
                else -> FloatArray(w * h) { ((code * 37) % 23 + 1) / 30f }
            }
        }
        GlyphAtlas(cellAspect = 0.5f, subW = w, subH = h, coverage = maps)
    }

    private fun ramp(text: String) = Alphabet(groups = emptySet(), extra = text, dedupe = 0f)

    // -----------------------------------------------------------------
    // Grid
    // -----------------------------------------------------------------

    @Test
    fun gridKeepsThePictureShapeForTheCell() {
        val size = TextArtRenderer.gridSize(1000, 500, TextArtSettings(columns = 100), atlas)
        // A cell 10 px wide is 20 px tall: 500 / 20 rows.
        assertArrayEquals(intArrayOf(100, 25), size)
        val square = TextArtRenderer.gridSize(1000, 500, TextArtSettings(columns = 100, cellAspect = 1f), atlas)
        assertArrayEquals(intArrayOf(100, 50), square)
    }

    @Test
    fun rowsSetStretchThePictureToTheGridAsked() {
        val settings = TextArtSettings(columns = 30, rows = 7)
        assertArrayEquals(intArrayOf(30, 7), TextArtRenderer.gridSize(1000, 500, settings, atlas))
        val picture = Raster(60, 60, IntArray(60 * 60) { 0xFFFFFFFF.toInt() })
        val art = TextArtRenderer.render(picture, settings.copy(alphabet = ramp(".#@")), atlas, ansi = false)
        assertEquals(30, art.columns)
        assertEquals(7, art.rows)
        // Out of bounds, rows are brought back in; 0 keeps the picture's shape.
        assertEquals(TextArtSettings.MIN_ROWS, TextArtRenderer.gridSize(100, 100, TextArtSettings(rows = 1), atlas)[1])
        assertEquals(TextArtSettings.MAX_ROWS, TextArtRenderer.gridSize(100, 100, TextArtSettings(rows = 99_999), atlas)[1])
        assertArrayEquals(intArrayOf(100, 25), TextArtRenderer.gridSize(1000, 500, TextArtSettings(columns = 100, rows = 0), atlas))
    }

    @Test
    fun theSizeIsSavedWithTheProject() {
        val edit = com.cuscus.ffmpeggui.core.model.ImageEdit(textArt = TextArtSettings(columns = 64, rows = 21))
        val back = com.cuscus.ffmpeggui.core.project.ModelJson.imageEdit(com.cuscus.ffmpeggui.core.project.ModelJson.write(edit))
        assertEquals(64, back.textArt.columns)
        assertEquals(21, back.textArt.rows)
        assertEquals(edit, back)
    }

    @Test
    fun columnsAreKeptWithinBounds() {
        assertEquals(TextArtSettings.MIN_COLUMNS, TextArtRenderer.gridSize(100, 100, TextArtSettings(columns = 1), atlas)[0])
        assertEquals(TextArtSettings.MAX_COLUMNS, TextArtRenderer.gridSize(100, 100, TextArtSettings(columns = 9999), atlas)[0])
    }

    // -----------------------------------------------------------------
    // Ramp
    // -----------------------------------------------------------------

    @Test
    fun rampFollowsTheLightOnDarkPaper() {
        val gradient = picture(200, 20) { x, _ -> grey(x * 255 / 199) }
        val art = TextArtRenderer.render(gradient, TextArtSettings(columns = 20, alphabet = ramp(".#@")), atlas, ansi = false)
        val row = art.row(0)
        // OKLab's lightness: the darkest cell (a mean of near blacks) is already a little light.
        assertTrue(row, row.first() in " .")
        assertEquals('@', row.last())
        // Never lighter further right.
        val order = " .#@"
        for (i in 1 until row.length) assertTrue(row, order.indexOf(row[i]) >= order.indexOf(row[i - 1]))
    }

    @Test
    fun lightPaperTurnsTheRampOver() {
        val gradient = picture(200, 20) { x, _ -> grey(x * 255 / 199) }
        val art = TextArtRenderer.render(gradient, TextArtSettings(columns = 20, paper = Paper.LIGHT, alphabet = ramp(".#@")), atlas, ansi = false)
        assertTrue(art.row(0), art.row(0).first() in "#@")
        assertTrue(art.row(0), art.row(0).last() in " .")
        assertFalse(art.row(0).last() == '@')
    }

    @Test
    fun theSameSettingsAlwaysMakeTheSameArt() {
        val noise = picture(120, 90) { x, y -> opaque(((x * 7919 + y * 104729) % 0xFFFFFF)) }
        val settings = TextArtSettings(columns = 40, dither = Dither.FLOYD_STEINBERG, mode = ArtMode.SHAPE, palette = ArtPalette(layer = ColorLayer.FOREGROUND_AND_BACKGROUND))
        val a = TextArtRenderer.render(noise, settings, atlas, ansi = true)
        val b = TextArtRenderer.render(noise, settings, atlas, ansi = true)
        assertArrayEquals(a.chars, b.chars)
        assertArrayEquals(a.fg, b.fg)
        assertArrayEquals(a.bg, b.bg)
    }

    @Test
    fun equalizingSpreadsADullPictureOverTheWholeRamp() {
        val dull = picture(200, 20) { x, _ -> grey(100 + x * 40 / 199) }
        val plain = TextArtRenderer.render(dull, TextArtSettings(columns = 20, alphabet = ramp(".#@")), atlas, ansi = false)
        val spread = TextArtRenderer.render(dull, TextArtSettings(columns = 20, alphabet = ramp(".#@"), tone = ArtTone(equalize = true)), atlas, ansi = false)
        assertTrue(plain.row(0).toSet().size < spread.row(0).toSet().size)
        assertEquals('@', spread.row(0).last())
    }

    @Test
    fun negativeSwapsInkAndPaper() {
        val white = picture(40, 40) { _, _ -> grey(255) }
        val art = TextArtRenderer.render(white, TextArtSettings(columns = 8, alphabet = ramp(".#@"), tone = ArtTone(invert = true)), atlas, ansi = false)
        assertTrue(art.row(0).all { it == ' ' })
    }

    @Test
    fun curveIsDrawnThroughItsPoints() {
        val points = listOf(0f, 0.5f, 0.5f, 0.5f, 1f)
        assertEquals(0.5f, TextArtRenderer.curveAt(points, 0.25f), 1e-6f)
        assertEquals(0.25f, TextArtRenderer.curveAt(points, 0.125f), 1e-6f)
        assertEquals(1f, TextArtRenderer.curveAt(points, 1f), 1e-6f)
    }

    // -----------------------------------------------------------------
    // Alphabet
    // -----------------------------------------------------------------

    @Test
    fun filtersKeepOutWhatTheTargetCannotTake() {
        val all = Alphabet(groups = GlyphGroup.entries.toSet(), filters = setOf(GlyphFilter.ASCII_ONLY))
        val resolved = Glyphs.resolve(all, atlas)
        assertTrue(resolved.ramp.all { it in 0x20..0x7E })
        assertEquals(GlyphState.FILTERED, resolved.keys.first { it.code == 0x2500 }.state)

        val markdown = Glyphs.resolve(TextArtPresets.ALL_ASCII.copy(filters = setOf(GlyphFilter.MARKDOWN_SAFE), dedupe = 0f), atlas)
        assertFalse(markdown.ramp.contains('*'.code))
        assertFalse(markdown.ramp.contains('`'.code))

        val comment = Glyphs.resolve(TextArtPresets.ALL_ASCII.copy(filters = setOf(GlyphFilter.COMMENT_SAFE), dedupe = 0f), atlas)
        assertFalse(comment.ramp.contains('*'.code))
        assertFalse(comment.ramp.contains('\\'.code))
        assertTrue(comment.ramp.contains('/'.code))

        val clear = Glyphs.resolve(TextArtPresets.ALL_ASCII.copy(filters = setOf(GlyphFilter.NO_LOOKALIKES), dedupe = 0f), atlas)
        for (c in "l1I|O0") assertFalse(clear.ramp.contains(c.code))
    }

    @Test
    fun safeWidthLeavesOnlyOneColumnCharacters() {
        val resolved = Glyphs.resolve(Alphabet(groups = GlyphGroup.entries.toSet(), filters = setOf(GlyphFilter.SAFE_WIDTH), dedupe = 0f), atlas)
        assertTrue(resolved.ramp.none { Glyphs.ambiguousWidth(it) })
        assertFalse(Glyphs.ambiguousWidth(Glyphs.BRAILLE_BASE + 0x41))
        assertTrue(Glyphs.ambiguousWidth(Glyphs.FULL_BLOCK))
    }

    @Test
    fun removedCharactersAreOffAndMissingOnesSaySo() {
        val noFont = GlyphAtlas(0.5f, 1, 1, mapOf('a'.code to floatArrayOf(0.4f)), missing = emptySet())
        val resolved = Glyphs.resolve(Alphabet(groups = emptySet(), extra = "abc", removed = "c", dedupe = 0f), noFont)
        val states = resolved.keys.associate { it.char to it.state }
        assertEquals(GlyphState.ACTIVE, states["a"])
        assertEquals(GlyphState.MISSING, states["b"])
        assertEquals(GlyphState.OFF, states["c"])
        assertArrayEquals(intArrayOf(' '.code, 'a'.code), resolved.ramp)
    }

    @Test
    fun dedupeKeepsOneOfTwoAlikeAndThePreferredOne() {
        val font = GlyphAtlas(0.5f, 1, 1, mapOf('a'.code to floatArrayOf(0.5f), 'b'.code to floatArrayOf(0.501f), 'c'.code to floatArrayOf(0.9f)))
        val plain = Glyphs.resolve(Alphabet(groups = emptySet(), extra = "abc", dedupe = 0.01f), font)
        assertArrayEquals(intArrayOf(' '.code, 'a'.code, 'c'.code), plain.ramp)
        assertEquals(GlyphState.DUPLICATE, plain.keys.first { it.char == "b" }.state)
        val preferred = Glyphs.resolve(Alphabet(groups = emptySet(), extra = "abc", dedupe = 0.01f, bias = mapOf("b" to 1f)), font)
        assertArrayEquals(intArrayOf(' '.code, 'b'.code, 'c'.code), preferred.ramp)
    }

    @Test
    fun cp437LeavesOutWhatItCannotWrite() {
        val resolved = Glyphs.resolve(Alphabet(groups = setOf(GlyphGroup.GEOMETRIC), dedupe = 0f), atlas, cp437 = true)
        assertEquals(GlyphState.UNENCODABLE, resolved.keys.first { it.code == 0x25CF }.state)
        assertEquals(GlyphState.ACTIVE, resolved.keys.first { it.code == 0x25A0 }.state)
    }

    @Test
    fun runsAndSharesAreLimited() {
        val flat = picture(400, 40) { _, _ -> grey(180) }
        val settings = TextArtSettings(columns = 40, alphabet = ramp(".#@").copy(maxRun = 2))
        val row = TextArtRenderer.render(flat, settings, atlas, ansi = false).row(0)
        assertFalse(row, Regex("([^ ])\\1\\1").containsMatchIn(row))

        val capped = TextArtRenderer.render(flat, TextArtSettings(columns = 40, alphabet = ramp(".#@").copy(maxShare = 0.25f)), atlas, ansi = false)
        val counts = capped.chars.filter { it != ' '.code }.groupingBy { it }.eachCount()
        assertTrue(counts.toString(), counts.values.all { it <= capped.drawnCells / 4 })
    }

    @Test
    fun biasPullsTowardsAPreferredCharacter() {
        // Half way in lightness: nearer the # than the dot, until the dot is preferred.
        val flat = picture(80, 40) { _, _ -> grey(99) }
        val plain = TextArtRenderer.render(flat, TextArtSettings(columns = 8, alphabet = ramp(".#@")), atlas, ansi = false)
        val biased = TextArtRenderer.render(flat, TextArtSettings(columns = 8, alphabet = ramp(".#@").copy(bias = mapOf("." to 1f))), atlas, ansi = false)
        assertFalse(plain.row(0).contains('.'))
        assertTrue(biased.row(0).all { it == '.' })
    }

    // -----------------------------------------------------------------
    // Other modes
    // -----------------------------------------------------------------

    @Test
    fun edgesDrawALineAlongTheEdge() {
        // Dark left half, light right half: a vertical edge in the middle.
        val split = picture(400, 200) { x, _ -> if (x < 200) grey(0) else grey(255) }
        val art = TextArtRenderer.render(split, TextArtSettings(columns = 20, mode = ArtMode.EDGES, tone = ArtTone(edgeFill = false)), atlas, ansi = false)
        val rows = (1 until art.rows - 1).map { art.row(it) }
        assertTrue(rows.toString(), rows.all { it.contains('|') })
        assertTrue(rows.all { r -> r.indexOf('|') in 8..11 })
    }

    @Test
    fun halfBlocksSplitACellInTwo() {
        // Light top half of every cell, dark bottom.
        val stripes = picture(80, 80) { _, y -> if ((y / 10) % 2 == 0) grey(255) else grey(0) }
        val art = TextArtRenderer.render(stripes, TextArtSettings(columns = 8, mode = ArtMode.HALF_BLOCKS), atlas, ansi = false)
        assertTrue(art.row(0), art.row(0).all { it.code == Glyphs.UPPER_HALF })
    }

    @Test
    fun halfBlocksInColourUseBothLayers() {
        val stripes = picture(80, 80) { _, y -> if ((y / 10) % 2 == 0) opaque(0xFF0000) else opaque(0x0000FF) }
        val settings = TextArtSettings(columns = 8, mode = ArtMode.HALF_BLOCKS, palette = ArtPalette(depth = ArtColor.TRUECOLOR, layer = ColorLayer.FOREGROUND_AND_BACKGROUND))
        val art = TextArtRenderer.render(stripes, settings, atlas, ansi = true)
        assertEquals(Glyphs.UPPER_HALF, art.chars[0])
        assertEquals(0xFF0000, art.fg[0])
        assertEquals(0x0000FF, art.bg[0])
    }

    @Test
    fun quadrantsFollowAChecker() {
        // Cells 20 x 40 px: in each, top left and bottom right lit.
        val checker = picture(160, 40) { x, y -> if ((x / 10 + y / 20) % 2 == 0) grey(255) else grey(0) }
        val art = TextArtRenderer.render(checker, TextArtSettings(columns = 8, cellAspect = 0.5f, mode = ArtMode.QUADRANTS), atlas, ansi = false)
        assertTrue(art.chars.all { it == 0x259A })
    }

    @Test
    fun brailleLightsEveryDotOfAWhiteCell() {
        val white = picture(20, 40) { _, _ -> grey(255) }
        val art = TextArtRenderer.render(white, TextArtSettings(columns = 8, mode = ArtMode.BRAILLE), atlas, ansi = false)
        assertTrue(art.chars.all { it == Glyphs.BRAILLE_BASE + 0xFF })
    }

    @Test
    fun brailleBecomesQuadrantsInCodePage437() {
        val settings = TextArtSettings(mode = ArtMode.BRAILLE, layout = ArtLayout(encoding = ArtEncoding.CP437))
        assertEquals(ArtMode.QUADRANTS, TextArtRenderer.modeOf(settings, ansi = true))
        assertEquals(ArtMode.BRAILLE, TextArtRenderer.modeOf(settings, ansi = false))
    }

    @Test
    fun textFillWritesThePhraseInOrder() {
        val white = picture(100, 20) { _, _ -> grey(255) }
        val art = TextArtRenderer.render(white, TextArtSettings(columns = 10, mode = ArtMode.TEXT_FILL, phrase = "abc"), atlas, ansi = false)
        assertEquals("abcabcabca", art.row(0))
    }

    @Test
    fun shadesUseTheFiveBlocks() {
        val gradient = picture(500, 20) { x, _ -> grey(x * 255 / 499) }
        val art = TextArtRenderer.render(gradient, TextArtSettings(columns = 8, mode = ArtMode.BLOCKS), atlas, ansi = false)
        val allowed = setOf(Glyphs.SPACE, Glyphs.LIGHT_SHADE, Glyphs.MEDIUM_SHADE, Glyphs.DARK_SHADE, Glyphs.FULL_BLOCK)
        assertTrue(art.chars.all { it in allowed })
        assertEquals(Glyphs.FULL_BLOCK, art.chars[art.columns - 1])
        assertTrue(art.chars.toSet().size >= 4)
    }

    @Test
    fun shapesFindTheLineThatIsThere() {
        // Cells of 4 x 8 px, one sub-cell a pixel, a light bar across the middle of each.
        val bar = picture(32, 8) { _, y -> if (y == 4) grey(255) else grey(0) }
        val art = TextArtRenderer.render(bar, TextArtSettings(columns = 8, mode = ArtMode.SHAPE, cellAspect = 0.5f, alphabet = Alphabet(groups = emptySet(), extra = "-|/\\.@", dedupe = 0f)), atlas, ansi = false)
        assertEquals("--------", art.row(0))
        val slash = picture(32, 8) { x, y -> if (x % 4 == (7 - y) / 2) grey(255) else grey(0) }
        assertEquals("////////", TextArtRenderer.render(slash, TextArtSettings(columns = 8, mode = ArtMode.SHAPE, cellAspect = 0.5f, alphabet = Alphabet(groups = emptySet(), extra = "-|/\\.@", dedupe = 0f)), atlas, ansi = false).row(0))
    }

    // -----------------------------------------------------------------
    // Hiding
    // -----------------------------------------------------------------

    @Test
    fun transparentPartsStayEmpty() {
        val half = picture(100, 50) { x, _ -> if (x < 50) 0x00FFFFFF else grey(255) }
        val art = TextArtRenderer.render(half, TextArtSettings(columns = 10, alphabet = ramp(".#@")), atlas, ansi = false)
        val row = art.row(0)
        assertTrue(row.substring(0, 5).all { it == ' ' })
        assertTrue(row.substring(5).all { it == '@' })
        assertEquals(art.rows * 5, art.drawnCells)
    }

    @Test
    fun hiddenColoursAndTheirOppositeShowWhatIsPicked() {
        val flag = picture(100, 50) { x, _ -> if (x < 50) opaque(0xFF0000) else opaque(0xFFFFFF) }
        val hide = ArtHide(colors = listOf(0xFF0000L))
        val art = TextArtRenderer.render(flag, TextArtSettings(columns = 10, alphabet = ramp(".#@"), hide = hide), atlas, ansi = false)
        assertTrue(art.row(0).substring(0, 5).isBlank())
        assertFalse(art.row(0).substring(5).isBlank())
        val only = TextArtRenderer.render(flag, TextArtSettings(columns = 10, alphabet = ramp(".#@"), hide = hide.copy(invert = true)), atlas, ansi = false)
        assertFalse(only.row(0).substring(0, 5).isBlank())
        assertTrue(only.row(0).substring(5).isBlank())
    }

    @Test
    fun hueBandsGoRoundTheWheel() {
        assertTrue(TextArtRenderer.inBand(350f, HueBand(340f, 20f)))
        assertTrue(TextArtRenderer.inBand(10f, HueBand(340f, 20f)))
        assertFalse(TextArtRenderer.inBand(180f, HueBand(340f, 20f)))
        val green = ArtColors.oklab(0x00FF00).hue
        val flag = picture(100, 50) { x, _ -> if (x < 50) opaque(0x00FF00) else grey(255) }
        val art = TextArtRenderer.render(flag, TextArtSettings(columns = 10, alphabet = ramp(".#@"), hide = ArtHide(hues = listOf(HueBand(green - 10f, green + 10f)))), atlas, ansi = false)
        assertTrue(art.row(0).substring(0, 5).isBlank())
        assertFalse(art.row(0).substring(5).isBlank())
    }

    @Test
    fun lightBandKeepsOnlyItsLights() {
        val gradient = picture(200, 20) { x, _ -> grey(x * 255 / 199) }
        val art = TextArtRenderer.render(gradient, TextArtSettings(columns = 20, alphabet = ramp(".#@"), hide = ArtHide(lightFrom = 0f, lightTo = 0.5f)), atlas, ansi = false)
        assertTrue(art.row(0).takeLast(5).isBlank())
    }

    @Test
    fun theBrushHidesAndShowsAgain() {
        val white = picture(100, 50) { _, _ -> grey(255) }
        val stroke = ArtStroke(points = listOf(0f, 0.5f, 0.3f, 0.5f), radius = 1f)
        val hidden = TextArtRenderer.render(white, TextArtSettings(columns = 10, alphabet = ramp(".#@"), hide = ArtHide(strokes = listOf(stroke))), atlas, ansi = false)
        assertTrue(hidden.chars.all { it == ' '.code })
        val shown = TextArtRenderer.render(white, TextArtSettings(columns = 10, alphabet = ramp(".#@"), hide = ArtHide(strokes = listOf(stroke, stroke.copy(show = true)))), atlas, ansi = false)
        assertTrue(shown.chars.all { it == '@'.code })
    }

    // -----------------------------------------------------------------
    // Colour
    // -----------------------------------------------------------------

    @Test
    fun sixteenColoursMatchTheTheme() {
        val red = picture(40, 40) { _, _ -> opaque(0xFF0000) }
        val art = TextArtRenderer.render(red, TextArtSettings(columns = 4, alphabet = ramp("#"), palette = ArtPalette(depth = ArtColor.ANSI_16, theme = TerminalTheme.XTERM)), atlas, ansi = true)
        assertEquals(9, art.fg[0])
        val text = String(TextArtWriter.ansiBytes(art, TextArtSettings(palette = ArtPalette(bright = BrightMode.AIXTERM))), Charsets.UTF_8)
        assertTrue(text, text.startsWith("\u001B[91m#"))
        assertTrue(text.endsWith("\n") && text.contains("\u001B[0m"))
        val bold = String(TextArtWriter.ansiBytes(art, TextArtSettings(palette = ArtPalette(bright = BrightMode.BOLD))), Charsets.UTF_8)
        assertTrue(bold, bold.startsWith("\u001B[1;31m#"))
    }

    @Test
    fun twoHundredFiftySixColoursUseTheCube() {
        val teal = picture(40, 40) { _, _ -> opaque((0 shl 16) or (95 shl 8) or 135) }
        // On light paper the teal is ink enough for the #.
        val art = TextArtRenderer.render(teal, TextArtSettings(columns = 4, paper = Paper.LIGHT, alphabet = ramp("#"), palette = ArtPalette(depth = ArtColor.ANSI_256, banned = (0..15).toSet())), atlas, ansi = true)
        assertEquals(16 + 0 * 36 + 1 * 6 + 2, art.fg[0])
        val text = String(TextArtWriter.ansiBytes(art, TextArtSettings()), Charsets.UTF_8)
        assertTrue(text, text.contains("38;5;24m"))
    }

    @Test
    fun trueColourIsExact() {
        val c = 0x12AB7F
        val art = TextArtRenderer.render(picture(40, 40) { _, _ -> opaque(c) }, TextArtSettings(columns = 4, alphabet = ramp("#"), palette = ArtPalette(depth = ArtColor.TRUECOLOR)), atlas, ansi = true)
        assertEquals(c, art.fg[0])
        val text = String(TextArtWriter.ansiBytes(art, TextArtSettings()), Charsets.UTF_8)
        assertTrue(text.contains("38;2;18;171;127m"))
    }

    @Test
    fun bannedEntriesAreNeverUsedAndRemapsRepaint() {
        val red = picture(40, 40) { _, _ -> opaque(0xFF0000) }
        val banned = TextArtRenderer.render(red, TextArtSettings(columns = 4, alphabet = ramp("#"), palette = ArtPalette(depth = ArtColor.ANSI_16, banned = setOf(9, 1))), atlas, ansi = true)
        assertTrue(banned.fg.none { it == 9 || it == 1 })
        val remapped = TextArtRenderer.render(red, TextArtSettings(columns = 4, alphabet = ramp("#"), palette = ArtPalette(depth = ArtColor.ANSI_16, remap = mapOf(9 to 12))), atlas, ansi = true)
        assertTrue(remapped.fg.all { it == 12 })
    }

    @Test
    fun darkBackgroundsOnlyWithoutBrightBackgrounds() {
        val settings = TextArtSettings(palette = ArtPalette(depth = ArtColor.ANSI_16, bright = BrightMode.BOLD, layer = ColorLayer.FOREGROUND_AND_BACKGROUND))
        val stripes = picture(40, 80) { _, y -> if ((y / 10) % 2 == 0) opaque(0xFFFFFF) else opaque(0xFFFF55) }
        val art = TextArtRenderer.render(stripes, settings.copy(columns = 4, mode = ArtMode.HALF_BLOCKS), atlas, ansi = true)
        assertTrue(art.bg.all { it < 8 })
    }

    @Test
    fun foregroundAndBackgroundShapesPickBothColours() {
        // Every cell: its top half red, its bottom half blue.
        val cells = picture(40, 80) { _, y -> if ((y % 20) < 10) opaque(0xFF0000) else opaque(0x0000FF) }
        val settings = TextArtSettings(columns = 10, mode = ArtMode.SHAPE, alphabet = Alphabet(groups = emptySet(), extra = "-|/\\.@#", dedupe = 0f).copy(extra = "-|/\\.@#\u2580"),
            palette = ArtPalette(depth = ArtColor.TRUECOLOR, layer = ColorLayer.FOREGROUND_AND_BACKGROUND))
        val art = TextArtRenderer.render(cells, settings, atlas, ansi = true)
        val i = art.columns + 1
        assertTrue(art.fg[i] >= 0 && art.bg[i] >= 0)
        assertTrue(setOf(art.fg[i], art.bg[i]) == setOf(0xFF0000, 0x0000FF))
    }

    @Test
    fun okLabComesBackToTheSameColour() {
        for (c in intArrayOf(0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x123456, 0x808080, 0xC0FFEE)) {
            assertEquals(Integer.toHexString(c), c, ArtColors.rgb(ArtColors.oklab(c)))
        }
    }

    @Test
    fun ciede2000MatchesThePublishedTestData() {
        // Sharma, Wu, Dalal (2005), pairs 1, 7 and 17.
        assertEquals(2.0425f, ArtColors.ciede2000(floatArrayOf(50f, 2.6772f, -79.7751f), floatArrayOf(50f, 0f, -82.7485f)), 1e-3f)
        assertEquals(2.3669f, ArtColors.ciede2000(floatArrayOf(50f, 0f, 0f), floatArrayOf(50f, -1f, 2f)), 1e-3f)
        assertEquals(27.1492f, ArtColors.ciede2000(floatArrayOf(50f, 2.5f, 0f), floatArrayOf(73f, 25f, -18f)), 1e-3f)
    }

    @Test
    fun xtermPaletteHasItsCubeAndGreys() {
        val p = ArtColors.xterm256(TerminalTheme.XTERM)
        assertEquals(256, p.size)
        assertEquals(0x005F87, p[24])
        assertEquals(0x080808, p[232])
        assertEquals(0xEEEEEE, p[255])
    }

    @Test
    fun clustersAreFewAndAlwaysTheSame() {
        val colors = IntArray(3000) { if (it % 3 == 0) 0xFF0000 else if (it % 3 == 1) 0x00FF00 else 0x0000FF }
        val a = ColorClusters.of(colors, 3)
        assertEquals(3, a.size)
        assertArrayEquals(a, ColorClusters.of(colors, 3))
        assertEquals(setOf(0xFF0000, 0x00FF00, 0x0000FF), a.toSet())
    }

    @Test
    fun bayerMatricesCoverEveryLevelOnce() {
        val four = (0 until 16).map { TextArtRenderer.bayer4(it % 4, it / 4) }.toSet()
        val eight = (0 until 64).map { TextArtRenderer.bayer8(it % 8, it / 8) }.toSet()
        assertEquals(16, four.size)
        assertEquals(64, eight.size)
        assertTrue(eight.all { it > 0f && it < 1f })
    }

    // -----------------------------------------------------------------
    // Files
    // -----------------------------------------------------------------

    @Test
    fun plainTextTrimsAndEndsItsLinesAsAsked() {
        val art = TextArt(3, 2, intArrayOf('a'.code, ' '.code, ' '.code, 'b'.code, 'c'.code, ' '.code), IntArray(6) { -1 }, IntArray(6) { -1 }, ArtColor.NONE, IntArray(0), Paper.DARK, 6)
        assertEquals("a\nbc\n", TextArtWriter.plain(art, ArtLayout()))
        assertEquals("a  \r\nbc \r\n", TextArtWriter.plain(art, ArtLayout(trimTrailing = false, lineEnding = LineEnding.CRLF)))
        val bom = TextArtWriter.plainBytes(art, ArtLayout(bom = true))
        assertEquals(0xEF.toByte(), bom[0])
        assertEquals('a'.code.toByte(), bom[3])
    }

    @Test
    fun cp437FilesHaveTheirBytesAndASauceRecord() {
        val art = TextArt(2, 1, intArrayOf(Glyphs.LIGHT_SHADE, Glyphs.FULL_BLOCK), intArrayOf(9, 4), intArrayOf(-1, 1), ArtColor.ANSI_16, ArtColors.theme(TerminalTheme.VGA), Paper.DARK, 2)
        val settings = TextArtSettings(layout = ArtLayout(encoding = ArtEncoding.CP437, lineEnding = LineEnding.CRLF, title = "Caff\u00E8", signature = "me"), palette = ArtPalette(depth = ArtColor.ANSI_16, iceColors = true))
        val bytes = TextArtWriter.ansiBytes(art, settings)
        val sauceAt = bytes.size - TextArtWriter.SAUCE_SIZE
        assertEquals(0x1A.toByte(), bytes[sauceAt - 1])
        assertEquals("SAUCE00", String(bytes, sauceAt, 7, Charsets.US_ASCII))
        // The bright red is bold, the shade is byte B0, the block DB.
        val body = String(bytes, 0, sauceAt - 1, Charsets.ISO_8859_1)
        assertTrue(body, body.startsWith("\u001B[0;1;31m\u00B0"))
        assertTrue(body.contains("\u001B[0;34;41m\u00DB"))
        assertTrue(body.endsWith("\u001B[0m\r\n"))
        // Title in code page 437 (e grave is 8A), the file size before the mark, the width, iCE.
        assertEquals(0x8A.toByte(), bytes[sauceAt + 7 + 4])
        val size = (bytes[sauceAt + 90].toInt() and 0xFF) or ((bytes[sauceAt + 91].toInt() and 0xFF) shl 8)
        assertEquals(sauceAt - 1, size)
        assertEquals(2, bytes[sauceAt + 96].toInt())
        assertEquals(1, bytes[sauceAt + 98].toInt())
        assertEquals(3, bytes[sauceAt + 105].toInt())
        // No date.
        assertTrue((82 until 90).all { bytes[sauceAt + it] == ' '.code.toByte() })
        assertEquals("IBM VGA", String(bytes, sauceAt + 106, 7, Charsets.US_ASCII))
    }

    @Test
    fun utf8ChangesOnlyWhatChanges() {
        val art = TextArt(3, 1, intArrayOf('a'.code, 'b'.code, 'c'.code), intArrayOf(196, 196, 21), intArrayOf(-1, -1, -1), ArtColor.ANSI_256, ArtColors.xterm256(TerminalTheme.XTERM), Paper.DARK, 3)
        val text = String(TextArtWriter.ansiBytes(art, TextArtSettings()), Charsets.UTF_8)
        assertEquals("\u001B[38;5;196mab\u001B[38;5;21mc\u001B[0m\n", text)
    }

    @Test
    fun codePage437TableAgreesWithItself() {
        assertEquals(0xB0, Cp437.byteOf(Glyphs.LIGHT_SHADE))
        assertEquals(0xDB, Cp437.byteOf(Glyphs.FULL_BLOCK))
        assertEquals(Glyphs.UPPER_HALF, Cp437.charOf(0xDF))
        for (b in 0x80..0xFF) assertEquals(b, Cp437.byteOf(Cp437.charOf(b)))
        assertEquals(null, Cp437.byteOf(0x2603))
    }

    @Test
    fun framesTitlesAndSignaturesAreLaidAround() {
        val art = TextArt(6, 1, IntArray(6) { '#'.code }, IntArray(6) { -1 }, IntArray(6) { -1 }, ArtColor.NONE, IntArray(0), Paper.DARK, 6)
        val framed = TextArtLayout.compose(art, ArtLayout(frame = FrameStyle.ASCII, margin = 1, title = "Hi", signature = "me"))
        assertEquals(10, framed.columns)
        assertEquals(5, framed.rows)
        assertEquals("+- Hi ---+", framed.row(0))
        assertEquals("|        |", framed.row(1))
        assertEquals("| ###### |", framed.row(2))
        assertEquals("+--- me -+", framed.row(4))
        val bare = TextArtLayout.compose(art, ArtLayout(title = "T", signature = "S"))
        assertEquals(listOf("T     ", "######", "     S"), (0 until bare.rows).map { bare.row(it) })
        assertEquals(0x250C, TextArtLayout.frameOf(FrameStyle.ROUNDED, cp437 = true)?.topLeft)
        assertEquals(0x256D, TextArtLayout.frameOf(FrameStyle.ROUNDED, cp437 = false)?.topLeft)
    }

    @Test
    fun reportsSayWhatToLookOutFor() {
        val art = TextArt(4, 1, intArrayOf('*'.code, Glyphs.FULL_BLOCK, 'a'.code, ' '.code), IntArray(4) { -1 }, IntArray(4) { -1 }, ArtColor.NONE, IntArray(0), Paper.DARK, 4)
        val report = TextArtReport.of(art, 10, TextArtSettings(layout = ArtLayout(maxWidth = 3)), atlas, ansi = false)
        assertTrue(ArtWarning.MARKDOWN_CHARS in report.warnings)
        assertTrue(ArtWarning.NON_ASCII in report.warnings)
        assertTrue(ArtWarning.AMBIGUOUS_WIDTH in report.warnings)
        assertTrue(ArtWarning.TOO_WIDE in report.warnings)
        assertEquals(3, report.characters.size)
        val bbs = TextArtReport.of(art, 10, TextArtSettings(layout = ArtLayout(encoding = ArtEncoding.CP437)), atlas, ansi = true)
        assertFalse(ArtWarning.AMBIGUOUS_WIDTH in bbs.warnings)
        assertFalse(ArtWarning.NON_ASCII in bbs.warnings)
        val empty = TextArt(2, 1, IntArray(2) { ' '.code }, IntArray(2) { -1 }, IntArray(2) { -1 }, ArtColor.NONE, IntArray(0), Paper.DARK, 0)
        assertTrue(ArtWarning.EMPTY in TextArtReport.of(empty, 0, TextArtSettings(), atlas, ansi = false).warnings)
    }

    @Test
    fun targetsSetWhatTheirPlaceNeeds() {
        val bbs = TextArtPresets.apply(TextTarget.BBS, TextArtSettings(mode = ArtMode.BRAILLE), ansi = true)
        assertEquals(ArtEncoding.CP437, bbs.layout.encoding)
        assertEquals(ArtColor.ANSI_16, bbs.palette.depth)
        assertEquals(LineEnding.CRLF, bbs.layout.lineEnding)
        assertEquals(80, bbs.columns)
        assertEquals(ArtMode.HALF_BLOCKS, bbs.mode)
        val chat = TextArtPresets.apply(TextTarget.CHAT, TextArtSettings(mode = ArtMode.BLOCKS), ansi = false)
        assertTrue(chat.alphabet.filters.containsAll(listOf(GlyphFilter.ASCII_ONLY, GlyphFilter.MARKDOWN_SAFE)))
        assertEquals(ArtMode.RAMP, chat.mode)
        assertEquals(TextArtSettings(), TextArtPresets.apply(TextTarget.BBS, TextArtSettings(), ansi = false))
    }

    @Test
    fun theShapesModeStartsFromEveryCharacter() {
        assertEquals(TextArtPresets.ALL_ASCII, TextArtPresets.alphabetFor(ArtMode.SHAPE, Alphabet()))
        assertEquals(Alphabet(), TextArtPresets.alphabetFor(ArtMode.RAMP, TextArtPresets.ALL_ASCII))
        val own = Alphabet(extra = "xo")
        assertEquals(own, TextArtPresets.alphabetFor(ArtMode.SHAPE, own))
        // Seventy steps with the space, which is always there.
        assertEquals(69, Glyphs.codes(Alphabet.DETAILED).size)
    }

    @Test
    fun flatCellsInTwoColoursAreSolid() {
        val flat = picture(80, 40) { _, _ -> opaque(0x336699) }
        val settings = TextArtSettings(columns = 8, mode = ArtMode.SHAPE, palette = ArtPalette(depth = ArtColor.TRUECOLOR, layer = ColorLayer.FOREGROUND_AND_BACKGROUND))
        val art = TextArtRenderer.render(flat, settings, atlas, ansi = true)
        assertTrue(art.chars.all { it == ' '.code })
        assertTrue(art.bg.all { it == 0x336699 })
    }

    @Test
    fun theLighterHalfIsTheCharacter() {
        // Dark top, light bottom: the lower half is drawn in the light colour.
        val stripes = picture(80, 80) { _, y -> if ((y / 10) % 2 == 0) opaque(0x000080) else opaque(0xFFFF55) }
        val settings = TextArtSettings(columns = 8, mode = ArtMode.HALF_BLOCKS, palette = ArtPalette(depth = ArtColor.ANSI_16, theme = TerminalTheme.VGA, bright = BrightMode.BOLD, layer = ColorLayer.FOREGROUND_AND_BACKGROUND))
        val art = TextArtRenderer.render(stripes, settings, atlas, ansi = true)
        assertEquals(Glyphs.LOWER_HALF, art.chars[0])
        assertEquals(11, art.fg[0])
        assertEquals(4, art.bg[0])
    }

    @Test
    fun anAsciiFileHasNoColours() {
        val red = picture(40, 40) { _, _ -> opaque(0xFF0000) }
        val art = TextArtRenderer.render(red, TextArtSettings(columns = 4, alphabet = ramp("#")), atlas, ansi = false)
        assertTrue(art.fg.all { it == -1 } && art.bg.all { it == -1 })
        assertEquals(ArtColor.NONE, art.depth)
    }
}
