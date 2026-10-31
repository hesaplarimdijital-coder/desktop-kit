package desktopkit.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import desktopkit.shell.NavKind
import desktopkit.shell.ShellScrollState
import desktopkit.shell.ShellTransitions
import desktopkit.shell.WidthClass
import desktopkit.shell.shellEnter
import desktopkit.shell.shellExit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which motion the kit picks for a window, and the bars' settle rule. */
class MotionTierTest {

    @Test
    fun phonesGetSpringsWiderWindowsKeepTheTimedCurves() {
        assertEquals(MotionTier.Gesture, DeskMotion.tier(WidthClass.Compact))
        for (wide in listOf(WidthClass.Medium, WidthClass.Expanded, WidthClass.Large, WidthClass.ExtraLarge)) {
            assertEquals(wide.name, MotionTier.Timed, DeskMotion.tier(wide))
        }
        for (any in WidthClass.entries) {
            assertEquals(any.name, MotionTier.Reduced, DeskMotion.tier(any, reduced = true))
        }
    }

    @Test
    fun spatialSpecFollowsTheTier() {
        val timed = tween<Float>(300)
        val spring = DeskMotion.spatial(MotionTier.Gesture, timed)
        assertTrue(spring is SpringSpec<*>)
        spring as SpringSpec<Float>
        assertEquals(Spring.StiffnessMediumLow, spring.stiffness)
        assertEquals(Spring.DampingRatioNoBouncy, spring.dampingRatio)
        assertSame(timed, DeskMotion.spatial(MotionTier.Timed, timed))
        assertTrue(DeskMotion.spatial(MotionTier.Reduced, timed) is SnapSpec<*>)
    }

    /**
     * The gesture spring arrives in time with a threshold for its unit: without one Compose's 0.01
     * keeps a bar that looks settled after 300 ms "running" for half a second more.
     */
    @Test
    fun gestureSpringArrivesInTimeWithAThreshold() {
        val travel = 170f // px: a hidden 64 dp bottom bar on the S24
        fun millis(spec: SpringSpec<Float>): Long =
            spec.vectorize(Float.VectorConverter).getDurationNanos(AnimationVector1D(0f), AnimationVector1D(travel), AnimationVector1D(0f)) / 1_000_000
        val bare = millis(DeskMotion.gesture())
        val pixels = millis(DeskMotion.gesture(0.5f))
        assertTrue("without a threshold the spring runs $bare ms", bare > 500)
        assertTrue("with half a pixel it is done in $pixels ms", pixels < 450)
        assertTrue(DeskMotion.spatial(MotionTier.Gesture, tween(300), 0.5f) is SpringSpec<*>)
        assertEquals(0.5f, (DeskMotion.spatial(MotionTier.Gesture, tween(300), 0.5f) as SpringSpec<Float>).visibilityThreshold)
    }

    @Test
    fun fadesStayTimedAndSnapWithReducedMotion() {
        val fade = tween<Float>(150)
        assertSame(fade, DeskMotion.fade(MotionTier.Gesture, fade))
        assertSame(fade, DeskMotion.fade(MotionTier.Timed, fade))
        assertTrue(DeskMotion.fade(MotionTier.Reduced, fade) is SnapSpec<*>)
    }

    @Test
    fun screenTransitionsByTier() {
        for (kind in NavKind.entries) {
            assertEquals(EnterTransition.None, shellEnter(ShellTransitions.Default, kind, MotionTier.Reduced))
            assertEquals(ExitTransition.None, shellExit(ShellTransitions.Default, kind, MotionTier.Reduced))
            assertEquals(EnterTransition.None, shellEnter(ShellTransitions.None, kind, MotionTier.Timed))
        }
        assertNotEquals(EnterTransition.None, shellEnter(ShellTransitions.Default, NavKind.BottomBar, MotionTier.Gesture))
        assertNotEquals(EnterTransition.None, shellEnter(ShellTransitions.Default, NavKind.Rail, MotionTier.Timed))
        assertNotEquals(EnterTransition.None, shellEnter(ShellTransitions.Default, NavKind.Sidebar, MotionTier.Timed))
    }

    @Test
    fun barsSettleHalfWay() {
        assertNull(ShellScrollState.settleTarget(0f, 64f))
        assertNull(ShellScrollState.settleTarget(64f, 64f))
        assertEquals(0f, ShellScrollState.settleTarget(20f, 64f))
        assertEquals(0f, ShellScrollState.settleTarget(32f, 64f))
        assertEquals(64f, ShellScrollState.settleTarget(33f, 64f))
    }

    @Test
    fun reducedMotionSettlesAtOnce() = runBlocking {
        val scroll = ShellScrollState()
        scroll.maxOffsetPx = 100f
        scroll.tier = MotionTier.Reduced
        // a drag that hid the bar 70 px: past half way, it finishes hiding without a frame clock
        scroll.connection.onPostScroll(
            androidx.compose.ui.geometry.Offset(0f, -70f),
            androidx.compose.ui.geometry.Offset.Zero,
            androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
        )
        assertEquals(70f, scroll.offsetPx)
        scroll.settle()
        assertEquals(100f, scroll.offsetPx)
    }
}
