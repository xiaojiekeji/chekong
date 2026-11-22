package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WidgetLockAlertSpecTest {

    private fun projectDir(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile ?: return@generateSequence null }
            .first { File(it, "app").isDirectory }
    }

    private fun textViewsOf(layoutFile: File): List<org.w3c.dom.Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layoutFile)
        val textViews = document.getElementsByTagName("TextView")
        return (0 until textViews.length).map { textViews.item(it) as org.w3c.dom.Element }
    }

    private fun List<org.w3c.dom.Element>.byId(id: String): org.w3c.dom.Element =
        single { it.getAttribute("android:id") == "@+id/$id" }

    /** 布局里所有带 id 的节点，圆点是一个 ImageView，不能只按 TextView 找。 */
    private fun allViewsOf(layoutFile: File): List<org.w3c.dom.Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layoutFile)
        val nodes = document.getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
    }

    @Test
    fun `updated time sits next to the car model title on the left`() {
        val layoutFile = File(projectDir(), "app/src/main/res/layout/widget_layout.xml")
        val layout = layoutFile.readText()
        val views = textViewsOf(layoutFile)
        val title = views.byId("txtWTitle")
        val updated = views.byId("txtWUpdated")

        // 时间紧跟在车型名之后，不再占用右上角。
        assertTrue(layout.indexOf("@+id/txtWTitle") < layout.indexOf("@+id/txtWUpdated"))
        assertEquals("0dp", updated.getAttribute("android:layout_width"))
        assertEquals("1", updated.getAttribute("android:layout_weight"))
        assertEquals("start|center_vertical", updated.getAttribute("android:gravity"))
        assertEquals("11sp", updated.getAttribute("android:textSize"))
        assertEquals("@color/widget_on_surface_variant", updated.getAttribute("android:textColor"))
        assertEquals("wrap_content", title.getAttribute("android:layout_width"))
    }

    @Test
    fun `top right corner hosts a status dot plus lock badge with two tones`() {
        val layoutFile = File(projectDir(), "app/src/main/res/layout/widget_layout.xml")
        val views = textViewsOf(layoutFile)
        val all = allViewsOf(layoutFile)
        val locked = views.byId("txtWLocked")
        val alert = views.byId("txtWLockAlert")
        val dot = all.byId("imgWLockDot")
        val row = all.byId("widgetLockStatusRow")

        // 灰色小字：已锁车
        assertEquals("已锁车", locked.getAttribute("android:text"))
        assertEquals("@color/widget_on_surface_variant", locked.getAttribute("android:textColor"))
        assertEquals("11sp", locked.getAttribute("android:textSize"))
        assertEquals("gone", locked.getAttribute("android:visibility"))
        assertEquals("1", locked.getAttribute("android:maxLines"))

        // 红色大字：未锁车
        assertEquals("未锁车", alert.getAttribute("android:text"))
        assertEquals("@color/alert_red", alert.getAttribute("android:textColor"))
        assertEquals("17sp", alert.getAttribute("android:textSize"))
        assertEquals("bold", alert.getAttribute("android:textStyle"))
        assertEquals("gone", alert.getAttribute("android:visibility"))
        assertEquals("1", alert.getAttribute("android:maxLines"))

        // 圆点：小尺寸 ImageView，默认绿点，文字前 4dp 间距。
        assertEquals("ImageView", dot.getTagName())
        assertEquals("7dp", dot.getAttribute("android:layout_width"))
        assertEquals("7dp", dot.getAttribute("android:layout_height"))
        assertEquals("4dp", dot.getAttribute("android:layout_marginEnd"))
        assertEquals("@drawable/widget_lock_dot_green", dot.getAttribute("android:src"))
        assertEquals("gone", dot.getAttribute("android:visibility"))

        // 状态区整排共用同一条右边界，切换状态时不会左右跳动。
        assertEquals("10dp", row.getAttribute("android:layout_marginEnd"))
        assertEquals("", locked.getAttribute("android:layout_marginEnd"))
        assertEquals("", alert.getAttribute("android:layout_marginEnd"))
    }

    @Test
    fun `status dot resources carry the matched colors`() {
        val resDir = File(projectDir(), "app/src/main/res/drawable")
        val green = File(resDir, "widget_lock_dot_green.xml").readText()
        val red = File(resDir, "widget_lock_dot_red.xml").readText()

        assertTrue(green.contains("@color/energy_green"))
        assertTrue(red.contains("@color/alert_red"))
        assertTrue(green.contains("oval"))
        assertTrue(red.contains("oval"))
    }

    @Test
    fun `renderer switches the lock badge only through the shared policy`() {
        val widget = File(projectDir(), "app/src/main/java/com/leapauto/app/ControlWidget.kt").readText()
        val worker = File(projectDir(), "app/src/main/java/com/leapauto/app/WidgetSyncWorker.kt").readText()

        assertTrue(widget.contains("fun applyTopRightStatus("))
        assertTrue(widget.contains("ParkedLockReminderPolicy.badge(locked, driving)"))
        assertTrue(widget.contains("R.id.txtWLocked"))
        assertTrue(widget.contains("R.id.txtWLockAlert"))
        assertTrue(widget.contains("R.id.imgWLockDot"))
        assertTrue(widget.contains("R.drawable.widget_lock_dot_green"))
        assertTrue(widget.contains("R.drawable.widget_lock_dot_red"))
        assertTrue(worker.contains("ControlWidget.applyTopRightStatus("))
        // 4x2 的时间文本只允许在统一入口里写一次，避免某一路刷新把它冲掉。
        assertEquals(1, widget.split("setTextViewText(R.id.txtWUpdated").size - 1)
        assertTrue(!worker.contains("setTextViewText(R.id.txtWUpdated"))
    }

    @Test
    fun `widget snapshot writes honour the optimistic lock target`() {
        val main = File(projectDir(), "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()
        val service = File(projectDir(), "app/src/main/java/com/leapauto/app/ControlService.kt").readText()
        val store = File(projectDir(), "app/src/main/java/com/leapauto/app/SessionStore.kt").readText()

        // 主页刷新写快照时不能直接用云端锁信号，否则刚解锁会被刷回「已锁车」。
        assertTrue(main.contains("val snapshotLocked = run {"))
        assertTrue(main.contains("lockProtected") || main.contains("lastLockActionEpochMs"))
        assertTrue(main.contains("sessionStore.resolveEffectiveLock("))
        assertTrue(service.contains("store.resolveEffectiveLock("))
        assertTrue(service.contains("SessionStore.APP_LOCK_TELEMETRY_GRACE_MS"))
        assertTrue(store.contains("APP_LOCK_TELEMETRY_GRACE_MS = 60_000L"))
    }

    @Test
    fun `lock actions push a full widget rebuild immediately`() {
        val widget = File(projectDir(), "app/src/main/java/com/leapauto/app/ControlWidget.kt").readText()
        val main = File(projectDir(), "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()
        val service = File(projectDir(), "app/src/main/java/com/leapauto/app/ControlService.kt").readText()
        val tile = File(projectDir(), "app/src/main/java/com/leapauto/app/tiles/LockToggleTileService.kt").readText()

        assertTrue(widget.contains("fun pushLockStateNow("))
        // 锁车/解锁的乐观状态翻转必须走全量推送，而不是增量刷新。
        assertTrue(main.split("ControlWidget.pushLockStateNow").size - 1 >= 3)
        // 控车回执在锁状态确定时也全量下发。
        assertTrue(service.contains("ControlWidget.showControlStatus("))
        assertTrue(widget.contains("manager.updateAppWidget(ids, update)"))
        // 控制中心快捷开关同样即时推送。
        assertTrue(tile.contains("pushLockStateNow(applicationContext)"))
    }
}
