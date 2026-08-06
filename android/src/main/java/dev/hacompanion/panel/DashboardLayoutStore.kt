package dev.hacompanion.panel

import android.content.Context
import java.io.File

class DashboardLayoutStore(context: Context) {
    private val store = AtomicLayoutFileStore(context.filesDir)

    fun load(): DashboardLayout = store.load() ?: DashboardLayout.default()

    fun save(layout: DashboardLayout) = store.save(layout)
}

class AtomicLayoutFileStore(private val directory: File) {
    private val target get() = File(directory, "dashboard-layout.json")

    fun load(): DashboardLayout? = try {
        target.takeIf(File::isFile)?.readText()?.let(DashboardLayout::parse)
    } catch (_: Exception) {
        null
    }

    fun save(layout: DashboardLayout) {
        directory.mkdirs()
        val temporary = File(directory, "dashboard-layout.json.tmp")
        temporary.writeText(layout.toJson().toString())
        check(temporary.renameTo(target)) { "Unable to activate dashboard layout" }
    }
}
