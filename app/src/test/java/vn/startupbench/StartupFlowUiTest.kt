package vn.startupbench

import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class StartupFlowUiTest {
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun androidOnlyIsDefaultAndLoadingAnotherPackageDoesNotOverwriteItsMarker() {
        val context = RuntimeEnvironment.getApplication()
        val first = ReadyCriterion("com.first", "com.first:id/home", "First")
        val second = ReadyCriterion("com.second", "com.second:id/home", "Second", autoContinue = false, blockerText = "Wait")
        ReadyCriterion.save(context, first); ReadyCriterion.save(context, second)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val optional = views(activity.window.decorView).filterIsInstance<CheckBox>().single { it.text.toString().startsWith("Đo thêm") }
            assertFalse(optional.isChecked)
            optional.performClick()
            fun packageField() = views(activity.window.decorView).filterIsInstance<EditText>().single { it.hint.toString() == "com.example.app" }
            packageField().setText("com.first")
            packageField().setText("com.second")
            assertEquals(second, ReadyCriterion.load(context, "com.second"))
            assertEquals(first, ReadyCriterion.load(context, "com.first"))
        } finally { controller.pause().stop().destroy() }
    }
}
