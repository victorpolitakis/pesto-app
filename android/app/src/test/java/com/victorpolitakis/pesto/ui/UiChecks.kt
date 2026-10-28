package com.victorpolitakis.pesto.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/*
 * Levels 2 and 3 of the UI validation, in the Robolectric tests of every screen
 * (level 1 is lintDebug with compose-lints). Level 2 (Δ11): checks on the bounds
 * of the semantics tree, without reference images. Level 3: Android's
 * accessibility checks (ATF). Both look at what is composed now: one screen,
 * without a dialog or a menu open.
 */

/** Half a pixel and more: rounding, not layout. */
private const val TOLERANCE = 1f

/** The smallest control a finger hits, as in Material and in ATF. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * Level 2: nothing goes off the sides of the screen or is cut at them, no text
 * runs past its box, and no two controls overlap (a control inside another,
 * like the bin of a row, is fine). The pages of a pager are off the sides on
 * purpose, so what is inside a horizontal scroller is left out.
 */
fun ComposeTestRule.assertLayout() {
    val problems = mutableListOf<String>()
    val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
    val width = root.size.width.toFloat()
    root.forEachOnScreen { node ->
        val whole = Rect(node.positionInRoot, node.size.toSize())
        if (whole.width <= 0f || whole.height <= 0f) return@forEachOnScreen
        if (whole.left < -TOLERANCE || whole.right > width + TOLERANCE) problems += "off the side of the screen: ${node.describe()}"
        // Cut at the top or the bottom is a list scrolling; at a side it is a layout that does not fit.
        val visible = node.boundsInRoot
        if (visible.height > 0f && visible.width < whole.width - TOLERANCE) problems += "cut at a side: ${node.describe()}"
        if (SemanticsProperties.EditableText !in node.config && node.textLayout()?.isCut() == true) {
            problems += "text wider than its box: ${node.describe()}"
        }
    }
    val controls = mutableListOf<SemanticsNode>()
    onRoot().fetchSemanticsNode().forEachOnScreen { node ->
        if (SemanticsActions.OnClick in node.config && !node.boundsInRoot.isEmpty) controls += node
    }
    for (i in controls.indices) {
        for (j in i + 1 until controls.size) {
            val a = controls[i]
            val b = controls[j]
            if (a.isAncestorOf(b) || b.isAncestorOf(a)) continue
            val common = a.boundsInRoot.intersect(b.boundsInRoot)
            if (common.width > TOLERANCE && common.height > TOLERANCE) problems += "controls overlap: ${a.describe()} and ${b.describe()}"
        }
    }
    if (problems.isNotEmpty()) fail(problems.distinct().joinToString("\n"))
}

/** Level 2: the nodes with [tags] start at the same x, as one column. */
fun ComposeTestRule.assertLeftAligned(vararg tags: String) {
    val lefts = tags.map { onNodeWithTag(it, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value }
    for (left in lefts) assertEquals("not aligned: ${tags.toList()}", lefts.first(), left, TOLERANCE)
}

/** Level 2: the node with [tag] has the middle of the screen as its middle. */
fun ComposeTestRule.assertCenteredOnScreen(tag: String) {
    val screen = onRoot().getUnclippedBoundsInRoot()
    val node = onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertEquals("not centered: $tag", (screen.left + screen.right).value / 2, (node.left + node.right).value / 2, TOLERANCE)
}

/**
 * Level 3: every control is at least 48dp on each side and has a label (a text
 * or a contentDescription). Spike P2: under Robolectric the accessibility checks
 * of Android (ATF) see a Compose screen as one view, with none of its nodes, so
 * these two run on the semantics tree. ATF stays for the Android views, and after
 * this call it also runs before every action of the test.
 * TODO(phase 6): the ATF checks on an emulator, where they see the Compose nodes.
 */
fun AndroidComposeTestRule<*, *>.assertAccessible() {
    val problems = mutableListOf<String>()
    val min = with(density) { MIN_TOUCH_TARGET.toPx() } - TOLERANCE
    onRoot().fetchSemanticsNode().forEachOnScreen { node ->
        // The whole layout node: Material keeps the clickable at 40dp and pads it to 48dp around it.
        val target = node.layoutInfo
        if (SemanticsActions.OnClick !in node.config || target.width == 0 || target.height == 0) return@forEachOnScreen
        if (target.width < min || target.height < min) problems += "touch target under 48dp: ${node.describe()}"
        val labels = listOf(SemanticsProperties.Text, SemanticsProperties.ContentDescription, SemanticsProperties.EditableText)
        if (labels.none { it in node.config }) problems += "control without a label: ${node.describe()}"
    }
    if (problems.isNotEmpty()) fail(problems.distinct().joinToString("\n"))
    enableAccessibilityChecks()
    onRoot().tryPerformAccessibilityChecks()
}

private fun SemanticsNode.forEachOnScreen(action: (SemanticsNode) -> Unit) {
    action(this)
    if (SemanticsProperties.HorizontalScrollAxisRange in config) return
    for (child in children) child.forEachOnScreen(action)
}

private fun SemanticsNode.isAncestorOf(other: SemanticsNode): Boolean {
    var parent = other.parent
    while (parent != null) {
        if (parent.id == id) return true
        parent = parent.parent
    }
    return false
}

private fun <T> SemanticsNode.find(key: SemanticsPropertyKey<T>): T? = if (key in config) config[key] else null

private fun SemanticsNode.textLayout(): TextLayoutResult? {
    val action = find(SemanticsActions.GetTextLayoutResult)?.action ?: return null
    val results = mutableListOf<TextLayoutResult>()
    action(results)
    return results.firstOrNull()
}

/**
 * Text that does not fit: cut at its last line, or a line past the side of its box. Not
 * didOverflowWidth, which is true for any text narrower than the space it was given. An
 * ellipsis is a cut on purpose, so it passes.
 */
private fun TextLayoutResult.isCut(): Boolean {
    if (layoutInput.overflow == TextOverflow.Ellipsis) return false
    return didOverflowHeight || (0 until lineCount).any { getLineRight(it) > size.width + TOLERANCE }
}

private fun SemanticsNode.describe(): String =
    find(SemanticsProperties.TestTag)
        ?: find(SemanticsProperties.Text)?.joinToString { it.text }
        ?: find(SemanticsProperties.ContentDescription)?.joinToString()
        ?: "node $id"
