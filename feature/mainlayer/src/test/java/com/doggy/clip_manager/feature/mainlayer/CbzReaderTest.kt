package com.doggy.clip_manager.feature.mainlayer

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.doggy.clip_manager.core.data.operation.ArchiveRepository
import com.doggy.clip_manager.core.data.operation.FileOperationSource
import com.doggy.clip_manager.core.testing.util.MainDispatcherRule
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Obligations O2-O5 (and ViewerScreen halves of O4/O5) of spec 513e8e0ad28ee777 v1.
 * Geometry: the reader is hosted in a fixed 300x440dp box; all tap coordinates are derived from
 * the measured `cbz_reader` node size and the spec zone formulas (square side = min(w,h)/3
 * around the center; strip = bottom 24dp, with no window insets in a plain test activity).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CbzReaderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var density: Density = Density(1f)

    /** (index, maxSidePx) per loadPage invocation. */
    private val calls = CopyOnWriteArrayList<Pair<Int, Int?>>()

    private fun bitmap(): Bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    private val okLoader: suspend (Int, Int?) -> Bitmap? = { i, m ->
        calls.add(i to m)
        bitmap()
    }

    private fun setReader(
        pageCount: Int,
        title: String = "book",
        loader: suspend (Int, Int?) -> Bitmap? = okLoader,
    ) {
        composeTestRule.setContent {
            density = LocalDensity.current
            Box(Modifier.size(300.dp, 440.dp)) {
                CbzReader(title = title, pageCount = pageCount, loadPage = loader, modifier = Modifier.fillMaxSize())
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun dp(v: Float): Float = with(density) { v.dp.toPx() }

    private fun root() = composeTestRule.onNodeWithTag(READER)

    private fun size(): Pair<Float, Float> {
        val s = root().fetchSemanticsNode().size
        return s.width.toFloat() to s.height.toFloat()
    }

    private fun tapAt(x: Float, y: Float) {
        root().performTouchInput { click(Offset(x, y)) }
        composeTestRule.waitForIdle()
    }

    private fun tapCenter() { val (w, h) = size(); tapAt(w / 2, h / 2) }
    private fun tapLeft() = tapAt(dp(30f), dp(80f))
    private fun tapRight() { val (w, _) = size(); tapAt(w - dp(30f), dp(80f)) }
    private fun tapStrip() { val (w, h) = size(); tapAt(w / 2, h - dp(12f)) }

    private fun swipe(startDp: Float, deltaDp: Float) {
        root().performTouchInput { swipe(Offset(dp(startDp), dp(80f)), Offset(dp(startDp + deltaDp), dp(80f))) }
        composeTestRule.waitForIdle()
    }

    /** Finger moves left by [dp]; spec R7: left movement means next. */
    private fun swipeLeft(dp: Float = 100f) = swipe(250f, -dp)
    private fun swipeRight(dp: Float = 100f) = swipe(40f, dp)

    private fun goToPage(page: Int) { repeat(page - 1) { tapRight() } }

    private fun badgeMatcher(text: String) =
        hasTestTag(BADGE) and (hasText(text) or hasAnyDescendant(hasText(text)))

    private fun assertBadge(text: String) {
        composeTestRule.onNode(badgeMatcher(text)).assertExists()
    }

    private fun assertTopBar(visible: Boolean, title: String = "book") {
        val bar = composeTestRule.onAllNodes(hasTestTag(TOP_BAR))
        if (visible) {
            bar.assertCountEquals(1)
            composeTestRule.onNode(hasTestTag(TOP_BAR) and (hasText(title) or hasAnyDescendant(hasText(title)))).assertExists()
        } else {
            bar.assertCountEquals(0)
        }
    }

    private fun assertPreview(open: Boolean) {
        composeTestRule.onAllNodes(hasTestTag(PREVIEW)).assertCountEquals(if (open) 1 else 0)
    }

    private fun thumb(i: Int): SemanticsNodeInteraction {
        val m = hasTestTag("cbz_thumb_$i")
        if (composeTestRule.onAllNodes(m).fetchSemanticsNodes().isEmpty()) {
            composeTestRule
                .onNode(hasScrollAction() and (hasTestTag(PREVIEW) or hasAnyAncestor(hasTestTag(PREVIEW))))
                .performScrollToNode(m)
            composeTestRule.waitForIdle()
        }
        return composeTestRule.onNode(m)
    }

    // ---------------------------------------------------------------- O2

    /** O2 Off-center -> On (C1). */
    @Test
    fun o2_offCenter_toOn_showsTopBarWithTitle_badgeUnchanged() {
        setReader(3, title = "My Title")
        goToPage(2)
        assertTopBar(false, "My Title")
        tapCenter()
        assertTopBar(true, "My Title")
        assertBadge("2/3")
    }

    /** O2 On-center -> Off (C2). */
    @Test
    fun o2_onCenter_toOff_hidesTopBar() {
        setReader(3)
        tapCenter()
        assertTopBar(true)
        tapCenter()
        assertTopBar(false)
        assertBadge("1/3")
    }

    /** O2 Off-strip -> Preview. */
    @Test
    fun o2_offStrip_opensPreview() {
        setReader(5)
        assertPreview(false)
        tapStrip()
        assertPreview(true)
    }

    /** O2 On-strip -> Preview. */
    @Test
    fun o2_onStrip_opensPreview() {
        setReader(5)
        tapCenter()
        assertTopBar(true)
        tapStrip()
        assertPreview(true)
    }

    /** O2 Preview-thumb -> prior UI state + page (C4), for prior UI off and on. */
    @Test
    fun o2_previewThumb_fromUiOff_goesToPage_closesPreview_uiStaysOff() {
        setReader(8)
        tapStrip()
        thumb(4).performClick()
        composeTestRule.waitForIdle()
        assertBadge("5/8")
        assertPreview(false)
        assertTopBar(false)
    }

    @Test
    fun o2_previewThumb_fromUiOn_goesToPage_closesPreview_uiStaysOn() {
        setReader(8)
        tapCenter()
        tapStrip()
        thumb(4).performClick()
        composeTestRule.waitForIdle()
        assertBadge("5/8")
        assertPreview(false)
        assertTopBar(true)
    }

    /** O2 Preview-outside/center -> prior UI state (R9, C14): {center,left,right} x {ui off,on}. */
    private fun previewDismiss(uiOn: Boolean, tap: () -> Unit) {
        setReader(5)
        goToPage(3)
        if (uiOn) tapCenter()
        tapStrip()
        assertPreview(true)
        tap()
        assertPreview(false)
        assertTopBar(uiOn)
        assertBadge("3/5")
    }

    @Test fun o2_previewCenterTap_uiOff_onlyCloses() = previewDismiss(false) { tapCenter() }
    @Test fun o2_previewCenterTap_uiOn_onlyCloses() = previewDismiss(true) { tapCenter() }
    @Test fun o2_previewLeftTap_uiOff_onlyCloses() = previewDismiss(false) { tapLeft() }
    @Test fun o2_previewLeftTap_uiOn_onlyCloses() = previewDismiss(true) { tapLeft() }
    @Test fun o2_previewRightTap_uiOff_onlyCloses() = previewDismiss(false) { tapRight() }
    @Test fun o2_previewRightTap_uiOn_onlyCloses() = previewDismiss(true) { tapRight() }

    /** R4: preview opens scrolled to the current page. */
    @Test
    fun o2_previewOpen_scrollsToCurrentPage() {
        setReader(40)
        goToPage(30)
        tapStrip()
        composeTestRule.onNodeWithTag("cbz_thumb_29").assertIsDisplayed()
        composeTestRule.onAllNodes(hasTestTag("cbz_thumb_0")).assertCountEquals(0)
    }

    // ---------------------------------------------------------------- O3

    private fun atPage(n: Int, total: Int = 3, action: () -> Unit, expected: String) {
        setReader(total)
        goToPage(n)
        assertBadge("$n/$total")
        action()
        assertBadge(expected)
    }

    @Test fun o3_page1_tapLeft_ignored() = atPage(1, action = { tapLeft() }, expected = "1/3")
    @Test fun o3_page1_tapRight_next() = atPage(1, action = { tapRight() }, expected = "2/3")
    @Test fun o3_page1_swipeLeft_next() = atPage(1, action = { swipeLeft() }, expected = "2/3")
    @Test fun o3_page1_swipeRight_ignored() = atPage(1, action = { swipeRight() }, expected = "1/3")
    @Test fun o3_pageN_tapLeft_prev() = atPage(3, action = { tapLeft() }, expected = "2/3")
    @Test fun o3_pageN_tapRight_ignored() = atPage(3, action = { tapRight() }, expected = "3/3")
    @Test fun o3_pageN_swipeLeft_ignored() = atPage(3, action = { swipeLeft() }, expected = "3/3")
    @Test fun o3_pageN_swipeRight_prev() = atPage(3, action = { swipeRight() }, expected = "2/3")

    @Test
    fun o3_singlePage_allInputsIgnored() {
        setReader(1)
        assertBadge("1/1")
        tapLeft(); assertBadge("1/1")
        tapRight(); assertBadge("1/1")
        swipeLeft(); assertBadge("1/1")
        swipeRight(); assertBadge("1/1")
    }

    @Test fun o3_swipeLeft_47dp_noChange() = atPage(1, action = { swipeLeft(47f) }, expected = "1/3")
    @Test fun o3_swipeLeft_49dp_next() = atPage(1, action = { swipeLeft(49f) }, expected = "2/3")
    @Test fun o3_swipeRight_47dp_noChange() = atPage(2, action = { swipeRight(47f) }, expected = "2/3")
    @Test fun o3_swipeRight_49dp_prev() = atPage(2, action = { swipeRight(49f) }, expected = "1/3")

    @Test
    fun o3_navigation_loadsNewMainPageWithNullMaxSide() {
        setReader(3)
        tapRight()
        composeTestRule.waitForIdle()
        assertTrue(calls.toString(), calls.contains(1 to null))
    }

    // ---------------------------------------------------------------- O4

    @Test
    fun o4_badge_boundsEqualOnAndOff_andTopLeft() {
        setReader(3)
        goToPage(2)
        val off = composeTestRule.onNodeWithTag(BADGE).getUnclippedBoundsInRoot()
        tapCenter()
        assertTopBar(true)
        val on = composeTestRule.onNodeWithTag(BADGE).getUnclippedBoundsInRoot()
        assertEquals(off, on)
        assertTrue("badge in upper-left quadrant: $on", on.left < 150.dp && on.top < 220.dp)
        assertBadge("2/3")
    }

    @Test
    fun o4_stripShade_onlyWhenUiOn() {
        setReader(3)
        composeTestRule.onAllNodes(hasTestTag(SHADE)).assertCountEquals(0)
        tapCenter()
        composeTestRule.onAllNodes(hasTestTag(SHADE)).assertCountEquals(1)
        tapCenter()
        composeTestRule.onAllNodes(hasTestTag(SHADE)).assertCountEquals(0)
    }

    private val navKeywords = listOf("이전", "다음", "뒤로", "back", "previous", "next", "skip")

    private val navLabelMatcher = SemanticsMatcher("has prev/next/back label") { node ->
        val cd = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val tx = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        (cd + tx).any { s -> navKeywords.any { k -> s.contains(k, ignoreCase = true) } }
    }

    private val clickOutsideReader =
        hasClickAction() and !hasTestTag(READER) and !hasAnyAncestor(hasTestTag(READER))

    @Test
    fun o4_noPrevNextBackControls_inReader_uiOffAndOn() {
        setReader(3, title = "book")
        goToPage(2)
        composeTestRule.onAllNodes(navLabelMatcher, useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodes(clickOutsideReader).assertCountEquals(0)
        tapCenter()
        composeTestRule.onAllNodes(navLabelMatcher, useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodes(clickOutsideReader).assertCountEquals(0)
    }

    @Test
    fun o4_thumbnailsRequestBoundedSize_mainPageRequestsFull() {
        setReader(6)
        composeTestRule.waitForIdle()
        assertTrue("main page requested with null: $calls", calls.contains(0 to null))
        assertTrue("no thumbnail before preview: $calls", calls.all { it.second == null })
        tapStrip()
        composeTestRule.waitForIdle()
        val thumbs = calls.filter { it.second != null }
        assertTrue("thumbnail loads expected: $calls", thumbs.isNotEmpty())
        val limit = ceil(dp(96f)).toInt()
        assertTrue("maxSidePx must be in 1..$limit: $thumbs", thumbs.all { it.second!! in 1..limit })
        assertTrue("main page never requested bounded: $calls", calls.filter { it.second == null }.all { it.first == 0 })
    }

    /** Supplementary to RV1: thumbnails load lazily, not all pages at once. */
    @Test
    fun o4_thumbnails_loadedLazily() {
        setReader(300)
        tapStrip()
        composeTestRule.waitForIdle()
        val distinct = calls.filter { it.second != null }.map { it.first }.toSet()
        assertTrue("distinct thumb loads=${distinct.size}", distinct.isNotEmpty() && distinct.size < 300)
    }

    // ---- ViewerScreen halves (R10, R8) -------------------------------------------------

    private fun pngBytes(): ByteArray = ByteArrayOutputStream().also {
        bitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun makeZip(name: String, entries: Map<String, ByteArray>): File {
        val f = File(tempFolder.root, name)
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return f
    }

    private fun openViewer(file: File) {
        val viewModel = ViewerViewModel(context, ArchiveRepository())
        composeTestRule.setContent {
            ViewerScreen(source = FileOperationSource.Local(file), onClose = {}, viewModel = viewModel)
        }
    }

    @Test
    fun o4_viewerScreen_title_stripsLastExtension_andNoBackButton() {
        val cbz = makeZip("my.book.cbz", mapOf("p1.png" to pngBytes(), "p2.png" to pngBytes()))
        openViewer(cbz)
        composeTestRule.waitUntilAtLeastOneExists(badgeMatcher("1/2"), 5_000)
        composeTestRule.onAllNodes(navLabelMatcher, useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodes(clickOutsideReader).assertCountEquals(0)
        root().performTouchInput { click(center) }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasTestTag(TOP_BAR) and (hasText("my.book") or hasAnyDescendant(hasText("my.book")))).assertExists()
        composeTestRule.onAllNodes(hasText("my.book.cbz"), useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodes(clickOutsideReader).assertCountEquals(0)
    }

    // ---------------------------------------------------------------- O5

    @Test
    fun o5_loadPageReturnsNull_badgeAndNavigationWork() {
        setReader(3, loader = { i, m -> calls.add(i to m); null })
        assertBadge("1/3")
        tapRight(); assertBadge("2/3")
        swipeRight(); assertBadge("1/3")
        tapCenter(); assertTopBar(true)
    }

    @Test
    fun o5_loadPageThrows_badgeAndNavigationWork() {
        setReader(3, loader = { i, m -> calls.add(i to m); throw IllegalStateException("decode failed") })
        assertBadge("1/3")
        tapRight(); assertBadge("2/3")
        swipeLeft(); assertBadge("3/3")
        tapCenter(); assertTopBar(true)
    }

    @Test
    fun o5_thumbnailFailures_leaveOtherThumbsAndJumpWorking() {
        val loader: suspend (Int, Int?) -> Bitmap? = { i, m ->
            calls.add(i to m)
            when {
                m == null -> bitmap()
                i == 0 -> null
                i == 1 -> throw IllegalStateException("thumb failed")
                else -> bitmap()
            }
        }
        setReader(5, loader = loader)
        tapStrip()
        composeTestRule.waitForIdle()
        thumb(0).assertExists()
        thumb(1).assertExists()
        for (i in 0..3) {
            val w = thumb(i).getUnclippedBoundsInRoot().let { it.right - it.left }
            assertTrue("thumb_$i width $w < 48dp", w >= 48.dp)
        }
        thumb(3).performClick()
        composeTestRule.waitForIdle()
        assertBadge("4/5")
        assertPreview(false)
    }

    @Test
    fun o5_viewerScreen_noImageEntries_showsEmptyMessage() {
        val cbz = makeZip("empty.cbz", mapOf("readme.txt" to "x".toByteArray()))
        openViewer(cbz)
        composeTestRule.waitUntilAtLeastOneExists(hasText("이미지가 없습니다"), 5_000)
        composeTestRule.onNodeWithTag(READER).assertDoesNotExist()
    }

    @Test
    fun o5_viewerScreen_zeroEntryArchive_showsEmptyMessage() {
        val f = File(tempFolder.root, "zero.cbz")
        // End-of-central-directory record only: a valid ZIP with 0 entries.
        f.writeBytes(byteArrayOf(0x50, 0x4B, 0x05, 0x06) + ByteArray(18))
        openViewer(f)
        composeTestRule.waitUntilAtLeastOneExists(hasText("이미지가 없습니다"), 5_000)
        composeTestRule.onNodeWithTag(READER).assertDoesNotExist()
    }

    /** R4 (v2): thumbnail cells keep at least 48dp width before load completes. */
    @Test
    fun o2_thumbnailCell_minWidth48dp_whileLoading() {
        setReader(5, loader = { i, m -> calls.add(i to m); if (m == null) bitmap() else kotlinx.coroutines.awaitCancellation() })
        tapStrip()
        composeTestRule.waitForIdle()
        for (i in 0..2) {
            val w = thumb(i).getUnclippedBoundsInRoot().let { it.right - it.left }
            assertTrue("thumb_$i width $w < 48dp", w >= 48.dp)
        }
    }

    // ---------------------------------------------------------------- O7 (density 2)

    private fun assertXhdpi() = assertEquals("test must run at density 2", 2f, density.density, 0f)

    private fun heightPx(n: SemanticsNodeInteraction): Float =
        n.getUnclippedBoundsInRoot().let { dp((it.bottom - it.top).value) }

    private fun assertNear(label: String, expectedPx: Float, actualPx: Float) =
        assertTrue("$label expected ${expectedPx}px (+-1) but was ${actualPx}px", kotlin.math.abs(expectedPx - actualPx) <= 1f)

    /** R3/Q3: strip top is h - 24dp = h - 48px at density 2 (bottom inset 0 in a plain activity). */
    @Test
    @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_stripTop_2pxAbove_doesNotOpenPreview() {
        setReader(3)
        assertXhdpi()
        val (w, h) = size()
        tapAt(w - dp(30f), h - dp(24f) - 2f)
        assertPreview(false)
        assertBadge("2/3")
    }

    @Test
    @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_stripTop_2pxBelow_opensPreview() {
        setReader(3)
        assertXhdpi()
        val (w, h) = size()
        tapAt(w - dp(30f), h - dp(24f) + 2f)
        assertPreview(true)
        assertBadge("1/3")
    }

    @Test
    @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_shadeHeight_is24dp() {
        setReader(3)
        assertXhdpi()
        tapCenter()
        assertNear("shade height", dp(24f), heightPx(composeTestRule.onNodeWithTag(SHADE)))
    }

    @Test
    @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_thumbHeight_is96dp() {
        setReader(5)
        assertXhdpi()
        tapStrip()
        composeTestRule.waitForIdle()
        for (i in 0..2) assertNear("thumb_$i height", dp(96f), heightPx(thumb(i)))
    }

    @Test
    @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_thumbMinWidth48dp_whileLoading() {
        setReader(5, loader = { i, m -> calls.add(i to m); if (m == null) bitmap() else kotlinx.coroutines.awaitCancellation() })
        assertXhdpi()
        tapStrip()
        composeTestRule.waitForIdle()
        for (i in 0..2) {
            val b = thumb(i).getUnclippedBoundsInRoot()
            val wPx = dp((b.right - b.left).value)
            assertTrue("thumb_$i width ${wPx}px < 96px", wPx >= dp(48f) - 1f)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_swipeLeft_47dp_noChange() { assertXhdpiAfter { atPage(1, action = { swipeLeft(47f) }, expected = "1/3") } }
    @Test @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_swipeLeft_49dp_next() { assertXhdpiAfter { atPage(1, action = { swipeLeft(49f) }, expected = "2/3") } }
    @Test @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_swipeRight_47dp_noChange() { assertXhdpiAfter { atPage(2, action = { swipeRight(47f) }, expected = "2/3") } }
    @Test @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_swipeRight_49dp_prev() { assertXhdpiAfter { atPage(2, action = { swipeRight(49f) }, expected = "1/3") } }

    private fun assertXhdpiAfter(body: () -> Unit) { body(); assertXhdpi() }

    @Test
    @Config(sdk = [35], qualifiers = "xhdpi")
    fun o7_thumbMaxSidePx_within96dpInPx() {
        setReader(6)
        assertXhdpi()
        tapStrip()
        composeTestRule.waitForIdle()
        val thumbs = calls.filter { it.second != null }
        assertTrue("thumbnail loads expected: $calls", thumbs.isNotEmpty())
        assertTrue("maxSidePx must be in 1..192: $thumbs", thumbs.all { it.second!! in 1..192 })
    }

    /** R8: other viewers keep a clickable back control (paired with the CBZ negative test above). */
    @Test
    fun o7_viewerScreen_textSource_hasClickableBackNode() {
        val txt = File(tempFolder.root, "note.txt").also { it.writeText("hello") }
        openViewer(txt)
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty())
    }

    private companion object {
        const val READER = "cbz_reader"
        const val BADGE = "cbz_page_badge"
        const val TOP_BAR = "cbz_top_bar"
        const val SHADE = "cbz_preview_strip_shade"
        const val PREVIEW = "cbz_preview"
    }
}
