package com.cuscus.ffmpeggui.core.project

import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.AudioEncoding
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.Keyframe
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import com.cuscus.ffmpeggui.core.timeline.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.random.Random

class ProjectJsonTest {

    // -----------------------------------------------------------------
    // JSON text
    // -----------------------------------------------------------------

    @Test
    fun textRoundTripKeepsEveryKindOfValue() {
        val value = jsonObject {
            put("s", "quote \" slash \\ line\n tab\t accent \u00E8 snowman \u2603")
            put("long", Long.MAX_VALUE)
            put("negative", -42)
            put("float", 1.0E-5f)
            put("double", 0.1)
            put("yes", true)
            put("none", null as String?)
            putList("list", listOf(1f, 2.5f)) { Json.of(it) }
            putList("objects", listOf(1, 2)) { jsonObject { put("n", it) } }
            put("empty", jsonObject { })
        }
        val text = JsonText.write(value)
        assertTrue("output must be ASCII", text.all { it.code < 128 })
        assertEquals(value, JsonText.parse(text))
    }

    @Test
    fun numbersAreReadBackExactly() {
        val o = JsonText.parse(JsonText.write(jsonObject {
            put("l", 1_712_345_678_901L)
            put("f", 0.1f)
            put("color", 0xFFFFFFFFL)
        })) as Json.Obj
        assertEquals(1_712_345_678_901L, o.long("l", 0L))
        assertEquals(0.1f, o.float("f", 0f))
        assertEquals(0xFFFFFFFFL, o.long("color", 0L))
    }

    @Test
    fun nonFiniteFloatsStayValidJson() {
        val text = JsonText.write(jsonObject { put("nan", Float.NaN); put("inf", Float.POSITIVE_INFINITY) })
        val o = JsonText.parse(text) as Json.Obj
        assertTrue(o.float("nan", 0f).isNaN())
        assertEquals(Float.POSITIVE_INFINITY, o.float("inf", 0f))
    }

    @Test(expected = JsonSyntaxException::class)
    fun brokenTextIsRejected() {
        JsonText.parse("{\"a\": 1,")
    }

    @Test(expected = JsonSyntaxException::class)
    fun trailingTextIsRejected() {
        JsonText.parse("{} x")
    }

    @Test
    fun readersFallBackOnDefaults() {
        val o = JsonText.parse("""{"rotation": "SIDEWAYS", "speed": "fast", "muted": 1, "unknownField": [1, 2]}""") as Json.Obj
        val effects = ModelJson.effects(o)
        assertEquals(Effects.NONE, effects)
    }

    @Test
    fun missingFieldsTakeTheModelDefaults() {
        val o = JsonText.parse("""{"rotation": "DEG_90"}""") as Json.Obj
        assertEquals(Effects.NONE.copy(rotation = Rotation.DEG_90), ModelJson.effects(o))
    }

    @Test
    fun objectsWithoutTheirRequiredFieldsAreLeftOut() {
        val o = JsonText.parse("""{"clips": [{"id": "a"}, {"id": "b", "sourceId": "main", "endMs": null}]}""") as Json.Obj
        val timeline = ModelJson.timeline(o)
        assertEquals(1, timeline.clips.size)
        assertEquals("b", timeline.clips[0].id)
        assertNull(timeline.clips[0].endMs)
    }

    // -----------------------------------------------------------------
    // Models: defaults and random values, through the text
    // -----------------------------------------------------------------

    @Test
    fun defaultsRoundTrip() {
        assertRoundTrip(Effects(), ModelJson::write, ModelJson::effects)
        assertRoundTrip(AudioEffects(), ModelJson::write, ModelJson::audioEffects)
        assertRoundTrip(ImageEdit(), ModelJson::write, ModelJson::imageEdit)
        assertRoundTrip(GifSettings(), ModelJson::write, ModelJson::gifSettings)
        assertRoundTrip(MetadataTags(), ModelJson::write, ModelJson::metadataTags)
        assertRoundTrip(AudioEncoding(), ModelJson::write, ModelJson::audioEncoding)
        assertRoundTrip(Timeline(), ModelJson::write, ModelJson::timeline)
        assertRoundTrip(TextOverlay(id = "t"), ModelJson::write) { ModelJson.textOverlay(it)!! }
    }

    /**
     * Every field of every model, filled with random values: a field the codec
     * forgets comes back as its default and fails the comparison.
     */
    @Test
    fun everyFieldRoundTrips() {
        repeat(RANDOM_RUNS) { seed ->
            val random = Random(seed)
            assertRoundTrip(random.instance(Effects::class.java), ModelJson::write, ModelJson::effects)
            assertRoundTrip(random.instance(AudioEffects::class.java), ModelJson::write, ModelJson::audioEffects)
            assertRoundTrip(random.instance(ImageEdit::class.java), ModelJson::write, ModelJson::imageEdit)
            assertRoundTrip(random.instance(GifSettings::class.java), ModelJson::write, ModelJson::gifSettings)
            assertRoundTrip(random.instance(MetadataTags::class.java), ModelJson::write, ModelJson::metadataTags)
            assertRoundTrip(random.instance(AudioEncoding::class.java), ModelJson::write, ModelJson::audioEncoding)
            assertRoundTrip(random.instance(Timeline::class.java), ModelJson::write, ModelJson::timeline)
            assertRoundTrip(random.instance(TextOverlay::class.java), ModelJson::write) { ModelJson.textOverlay(it)!! }
            assertRoundTrip(random.instance(SoundSpan::class.java), ModelJson::write) { ModelJson.soundSpan(it)!! }
            assertRoundTrip(random.instance(MediaInfo::class.java), ModelJson::write) { ModelJson.mediaInfo(it)!! }
        }
    }

    @Test
    fun randomInstancesDifferFromTheDefaults() {
        // Guards the test above: it only proves something if the values are not the defaults.
        val random = Random(1)
        assertFalse(random.instance(Effects::class.java) == Effects())
        assertFalse(random.instance(ImageEdit::class.java) == ImageEdit())
    }

    private fun <T> assertRoundTrip(value: T, write: (T) -> Json.Obj, read: (Json.Obj) -> T) {
        val text = JsonText.write(write(value))
        assertEquals(value, read(JsonText.parse(text) as Json.Obj))
    }

    private companion object {
        const val RANDOM_RUNS = 40
    }
}

/**
 * A random instance of a data class of the models, built through its primary
 * constructor (the one with as many parameters as it has components).
 */
internal fun <T> Random.instance(type: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return randomValue(type, 0) as T
}

private fun Random.randomValue(type: Type, depth: Int): Any = when {
    type is WildcardType -> randomValue(type.upperBounds[0], depth)
    type is ParameterizedType && (type.rawType as Class<*>) == List::class.java ->
        List(if (depth > 3) 0 else nextInt(0, 4)) { randomValue(type.actualTypeArguments[0], depth + 1) }
    type is ParameterizedType && (type.rawType as Class<*>) == Map::class.java ->
        List(nextInt(0, 4)) { randomValue(type.actualTypeArguments[0], depth + 1) to randomValue(type.actualTypeArguments[1], depth + 1) }.toMap()
    type !is Class<*> -> error("Unsupported type $type")
    // Keyframes are kept in time order.
    type == Keyframes::class.java -> Keyframes(
        List(if (depth > 3) 0 else nextInt(0, 4)) { randomValue(Keyframe::class.java, depth + 1) as Keyframe }
            .distinctBy { it.atMs }.sortedBy { it.atMs },
    )
    type == java.lang.Long.TYPE || type == java.lang.Long::class.java -> nextLong(0L, 10_000_000L)
    type == Integer.TYPE || type == Integer::class.java -> nextInt(0, 100_000)
    type == java.lang.Float.TYPE || type == java.lang.Float::class.java -> nextInt(-10_000, 10_000) / 997f
    type == java.lang.Double.TYPE || type == java.lang.Double::class.java -> nextInt(-10_000, 10_000) / 991.0
    type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java -> nextBoolean()
    type == String::class.java -> "s${nextInt(1000)} \u00E8 \"\\ \u2603"
    type.isEnum -> type.enumConstants.let { it[nextInt(it.size)] }
    type == MediaInfo::class.java -> MediaInfo.of(
        nextLong(0L, 10_000_000L), nextBoolean(), nextInt(0, 4000), nextInt(0, 4000),
        nextLong(0L, 1L shl 40), nextBoolean(), nextBoolean(),
        frameRate = nextInt(0, 480) / 4f,
    )
    else -> {
        val components = type.methods.count { it.name.matches(Regex("component\\d+")) && it.parameterCount == 0 }
        val constructor = type.constructors.first { c -> c.parameterCount == components && !c.isSynthetic }
        val args = constructor.genericParameterTypes.map { randomValue(it, depth + 1) }
        constructor.newInstance(*args.toTypedArray())
    }
}
