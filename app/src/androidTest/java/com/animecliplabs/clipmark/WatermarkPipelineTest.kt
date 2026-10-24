package com.animecliplabs.clipmark

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.animecliplabs.clipmark.core.batch.BatchItem
import com.animecliplabs.clipmark.core.batch.BatchJob
import com.animecliplabs.clipmark.core.batch.BatchRunner
import com.animecliplabs.clipmark.core.batch.FailureKind
import com.animecliplabs.clipmark.core.batch.ItemOutcome
import com.animecliplabs.clipmark.core.batch.JobSink
import com.animecliplabs.clipmark.core.model.OutputFormatPreference
import com.animecliplabs.clipmark.core.model.OutputSettings
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import com.animecliplabs.clipmark.image.ExifCopier
import com.animecliplabs.clipmark.image.ImageLoader
import com.animecliplabs.clipmark.image.MediaStoreOutputStore
import com.animecliplabs.clipmark.image.OutputStore
import com.animecliplabs.clipmark.image.SavedOutput
import com.animecliplabs.clipmark.image.WatermarkAnalyzer
import com.animecliplabs.clipmark.image.WatermarkProcessor
import com.animecliplabs.clipmark.image.WatermarkRenderer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/**
 * End-to-end checks of the real pipeline (ImageDecoder, Canvas, Bitmap.compress, ExifInterface,
 * MediaStore) on a device. Sample images are generated here, so no test assets are needed.
 */
@RunWith(AndroidJUnit4::class)
class WatermarkPipelineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var outDir: File
    private val renderer = WatermarkRenderer()
    private val loader = ImageLoader(context)

    /** Writes outputs to a folder so the test can inspect bytes without touching the gallery. */
    private inner class FolderStore : OutputStore {
        override fun isTaken(displayName: String) = File(outDir, displayName).exists()
        override fun save(file: File, displayName: String, mimeType: String): SavedOutput {
            val dest = File(outDir, displayName)
            file.copyTo(dest)
            return SavedOutput(Uri.fromFile(dest), displayName)
        }
    }

    private fun processor(store: OutputStore = FolderStore()) = WatermarkProcessor(
        loader, renderer, WatermarkAnalyzer(renderer), ExifCopier(context.contentResolver), store, File(dir, "tmp"),
    )

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "pipeline-test").apply { deleteRecursively(); mkdirs() }
        outDir = File(dir, "out").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    // ---- sample generation ------------------------------------------------------------------

    private fun artwork(w: Int, h: Int, alpha: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.rgb(40, 30, 90), Color.rgb(250, 180, 150), Shader.TileMode.CLAMP)
        if (alpha) {
            c.drawColor(Color.TRANSPARENT)
            c.drawCircle(w / 2f, h / 2f, minOf(w, h) / 3f, p)
        } else {
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        p.shader = null
        val r = Random(1)
        repeat(200) {
            p.color = Color.argb(255, r.nextInt(256), r.nextInt(256), r.nextInt(256))
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.6f, 4f + r.nextFloat() * 20f, p)
        }
        return bmp
    }

    private fun write(name: String, bmp: Bitmap, format: Bitmap.CompressFormat, sub: String = ""): File {
        val f = File(File(dir, sub).apply { mkdirs() }, name)
        f.outputStream().use { bmp.compress(format, 95, it) }
        return f
    }

    private fun job(vararg files: File, output: OutputSettings = OutputSettings()) = BatchJob(
        id = "t", createdAt = 0, presetName = "AnimeClips Default", style = WatermarkStyle(), output = output,
        items = files.map { BatchItem(Uri.fromFile(it).toString(), it.name) },
    )

    private suspend fun processOne(file: File, output: OutputSettings = OutputSettings()): ItemOutcome {
        val j = job(file, output = output)
        return processor().process(j, j.items.single(), mutableSetOf())
    }

    private fun bounds(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        return o.outWidth to o.outHeight
    }

    private fun decode(file: File): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }

    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    private fun savedFile(outcome: ItemOutcome): File {
        assertTrue("expected Saved but was $outcome", outcome is ItemOutcome.Saved)
        return File(outDir, (outcome as ItemOutcome.Saved).name)
    }

    // ---- tests ------------------------------------------------------------------------------

    @Test
    fun jpegKeepsFullResolutionAspectAndFormat() = runTest {
        val src = write("photo.jpg", artwork(4000, 3000), Bitmap.CompressFormat.JPEG)
        val out = savedFile(processOne(src))
        assertEquals("photo_watermarked.jpg", out.name)
        assertEquals(4000 to 3000, bounds(out))
        assertEquals("image/jpeg", BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(out.path, it) }.outMimeType)
    }

    @Test
    fun originalFileIsNeverModified() = runTest {
        val src = write("keep.png", artwork(1200, 900), Bitmap.CompressFormat.PNG)
        val before = sha(src)
        val modified = src.lastModified()
        savedFile(processOne(src))
        assertArrayEquals(before, sha(src))
        assertEquals(modified, src.lastModified())
    }

    @Test
    fun pngPixelsOutsideTheWatermarkAreBitIdentical() = runTest {
        val src = write("frame.png", artwork(1080, 1350), Bitmap.CompressFormat.PNG)
        val out = savedFile(processOne(src))
        val a = decode(src)
        val b = decode(out)
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)

        val placement = requireNotNull(renderer.place(a.width.toFloat(), a.height.toFloat(), WatermarkStyle()))
        val bleed = placement.textSize * 0.4f + 2
        var changedInside = 0
        for (y in 0 until a.height step 3) for (x in 0 until a.width step 3) {
            val inside = x >= placement.left - bleed && x <= placement.right + bleed &&
                y >= placement.top - bleed && y <= placement.bottom + bleed
            if (inside) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) changedInside++
            } else {
                assertEquals("pixel ($x,$y) changed outside the watermark", a.getPixel(x, y), b.getPixel(x, y))
            }
        }
        assertTrue("watermark should actually be drawn", changedInside > 50)
    }

    @Test
    fun webpStaysWebpAndKeepsSize() = runTest {
        val src = write("clip.webp", artwork(1200, 800), Bitmap.CompressFormat.WEBP_LOSSY)
        val out = savedFile(processOne(src))
        assertTrue(out.name.endsWith(".webp"))
        assertEquals(1200 to 800, bounds(out))
    }

    @Test
    fun transparencyIsPreserved() = runTest {
        val src = write("sticker.png", artwork(800, 800, alpha = true), Bitmap.CompressFormat.PNG)
        val out = decode(savedFile(processOne(src, OutputSettings(format = OutputFormatPreference.JPEG))))
        assertTrue(out.hasAlpha())
        assertEquals(0, Color.alpha(out.getPixel(2, 2)))
    }

    @Test
    fun exifRotatedPhotoIsExportedUprightWithoutDoubleRotation() = runTest {
        // Stored landscape 400×300 with "rotate 90" — galleries show it as portrait 300×400.
        val src = write("rotated.jpg", artwork(400, 300), Bitmap.CompressFormat.JPEG)
        ExifInterface(src).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:09:20 18:30:00")
            setAttribute(ExifInterface.TAG_MODEL, "TestCam")
            setLatLong(35.6762, 139.6503)
            saveAttributes()
        }
        val out = savedFile(processOne(src))
        assertEquals(300 to 400, bounds(out))
        val exif = ExifInterface(out)
        assertEquals(ExifInterface.ORIENTATION_NORMAL, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
        assertEquals("2026:09:20 18:30:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("TestCam", exif.getAttribute(ExifInterface.TAG_MODEL))
        assertNull("location must be stripped by default", exif.latLong)
    }

    @Test
    fun locationIsKeptOnlyWhenAllowed() = runTest {
        val src = write("geo.jpg", artwork(400, 300), Bitmap.CompressFormat.JPEG)
        ExifInterface(src).apply { setLatLong(35.6762, 139.6503); saveAttributes() }
        val out = savedFile(processOne(src, OutputSettings(stripLocation = false)))
        val latLong = requireNotNull(ExifInterface(out).latLong)
        assertEquals(35.6762, latLong[0], 1e-3)
    }

    @Test
    fun corruptedAndTruncatedFilesFailGracefully() = runTest {
        val garbage = File(dir, "garbage.jpg").apply { writeBytes(Random(3).nextBytes(20_000)) }
        val full = write("half.jpg", artwork(1000, 800), Bitmap.CompressFormat.JPEG).readBytes()
        val truncated = File(dir, "truncated.jpg").apply { writeBytes(full.copyOf(full.size / 3)) }
        for (f in listOf(garbage, truncated)) {
            val outcome = processOne(f)
            assertTrue("$f → $outcome", outcome is ItemOutcome.Failed)
            val kind = (outcome as ItemOutcome.Failed).kind
            assertTrue("$f → $kind", kind == FailureKind.UNREADABLE || kind == FailureKind.UNSUPPORTED)
        }
        assertEquals(0, outDir.listFiles()!!.size)
    }

    @Test
    fun missingFileIsReportedAsNotFound() = runTest {
        val outcome = processOne(File(dir, "gone.jpg"))
        assertEquals(ItemOutcome.Failed(FailureKind.NOT_FOUND), outcome)
    }

    @Test
    fun batchSurvivesABadImage() = runTest {
        val good1 = write("a.jpg", artwork(800, 600), Bitmap.CompressFormat.JPEG)
        val bad = File(dir, "b.jpg").apply { writeBytes(ByteArray(100) { 7 }) }
        val good2 = write("c.png", artwork(600, 800), Bitmap.CompressFormat.PNG)
        val result = BatchRunner(processor(), JobSink { }).run(job(good1, bad, good2))
        assertEquals(2, result.savedCount)
        assertEquals(1, result.failedCount)
        assertEquals(setOf("a_watermarked.jpg", "c_watermarked.png"), outDir.list()!!.toSet())
    }

    @Test
    fun sameNameFromDifferentFoldersNeverCollides() = runTest {
        val one = write("IMG_0001.jpg", artwork(500, 500), Bitmap.CompressFormat.JPEG, sub = "cam")
        val two = write("IMG_0001.jpg", artwork(500, 500), Bitmap.CompressFormat.JPEG, sub = "screens")
        File(outDir, "IMG_0001_watermarked.jpg").writeText("already here from last week")
        val result = BatchRunner(processor(), JobSink { }).run(job(one, two))
        assertEquals(
            listOf("IMG_0001_watermarked_2.jpg", "IMG_0001_watermarked_3.jpg"),
            result.items.map { it.outputName },
        )
        assertEquals("already here from last week", File(outDir, "IMG_0001_watermarked.jpg").readText())
    }

    @Test
    fun hugeImagesAreReducedOnlyWhenMemoryRequiresIt_keepingAspect() {
        val src = write("huge.jpg", artwork(4000, 3000), Bitmap.CompressFormat.JPEG)
        val normal = loader.decodeFull(Uri.fromFile(src))
        assertEquals(false, normal.downscaled)
        assertEquals(4000, normal.bitmap.width)
        normal.bitmap.recycle()

        val constrained = loader.decodeFull(Uri.fromFile(src), maxPixels = 3_000_000)
        assertTrue(constrained.downscaled)
        assertTrue(constrained.bitmap.width.toLong() * constrained.bitmap.height <= 3_000_000)
        assertEquals(4f / 3f, constrained.bitmap.width / constrained.bitmap.height.toFloat(), 0.01f)
        constrained.bitmap.recycle()
    }

    @Test
    fun savesIntoTheGalleryFolderWithDuplicateSafety() = runTest {
        val store = MediaStoreOutputStore(context)
        val unique = "cmtest_${System.nanoTime()}"
        val src = write("$unique.png", artwork(300, 300), Bitmap.CompressFormat.PNG)
        val saved = mutableListOf<Uri>()
        try {
            repeat(2) {
                val j = job(src)
                val outcome = processor(store).process(j, j.items.single(), mutableSetOf())
                saved += Uri.parse((outcome as ItemOutcome.Saved).uri)
            }
            val names = saved.map { uri ->
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)!!.use {
                    it.moveToFirst()
                    assertEquals(MediaStoreOutputStore.RELATIVE_PATH, it.getString(1))
                    it.getString(0)
                }
            }
            assertEquals(listOf("${unique}_watermarked.png", "${unique}_watermarked_2.png"), names)
            assertNotEquals(saved[0], saved[1])
            assertTrue(ContentUris.parseId(saved[0]) > 0)
        } finally {
            saved.forEach { context.contentResolver.delete(it, null, null) }
        }
    }
}
