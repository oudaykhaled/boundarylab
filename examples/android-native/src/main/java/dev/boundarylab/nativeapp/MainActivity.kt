package dev.boundarylab.nativeapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** SAF grants stay scoped to this Activity. Rotation/disposal requires a fresh attempt. */
class MainActivity : Activity() {
    private lateinit var controller: PackController
    private lateinit var status: TextView
    private lateinit var files: LinearLayout
    private lateinit var sampleResult: TextView
    private lateinit var select: Button
    private lateinit var prepare: Button
    private lateinit var cancel: Button
    private lateinit var export: Button
    private lateinit var sample: Button
    private lateinit var progress: ProgressBar
    private val writes = Executors.newSingleThreadExecutor()
    private var exportBytes: ByteArray? = null
    private var exporting = false
    private var exportToken = -1L
    private lateinit var historyText: TextView
    private var wasBusy = false
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = PackController(this, ::render)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.background)
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                insets
            }
        }
        val page = column().apply { setPadding(dp(24), dp(32), dp(24), dp(32)) }
        scroll.addView(page)
        val header = column().apply { background = shape(Palette.navy); setPadding(dp(24), dp(24), dp(24), dp(24)) }
        header.addView(text("BoundaryLab", 14, Color.WHITE, true))
        header.addView(text("FieldPack", 28, Color.WHITE, true))
        header.addView(text("Inspection handoff", 20, Color.WHITE))
        header.addView(text("Prepare a record of your attachments for a clearer handoff.", 16, Color.WHITE))
        page.addView(header)
        page.addView(text("01  SELECT    ·    02  PREPARE    ·    03  EXPORT", 14, Palette.emerald, true), spaced())
        page.addView(text("Your attachments", 20, Palette.ink, true), spaced())
        page.addView(text("Choose 1–32 stable files, up to 128 MiB in total. Local regular files are supported; some providers may need a connection.", 16))
        select = action("Choose attachments", R.id.select_attachments) { pick() }
        page.addView(select, spaced())
        files = column().apply { id = R.id.attachment_list }
        page.addView(files, spaced())
        val panel = column().apply { background = shape(Color.WHITE); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        status = text("", 16, Palette.ink, true).apply { id = R.id.pack_status; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        panel.addView(status)
        progress = ProgressBar(this).apply { contentDescription = "Preparing attachments"; isIndeterminate = true }
        panel.addView(progress)
        prepare = action("Prepare pack", R.id.prepare_pack) { controller.prepare() }
        cancel = action("Request cancellation", R.id.cancel_attempt, false) { controller.cancel() }
        export = action("Export manifest", R.id.export_manifest) { requestExport() }
        panel.addView(prepare, spaced()); panel.addView(cancel, spaced()); panel.addView(export, spaced())
        page.addView(panel, spaced())
        page.addView(text("Transfer the attachments separately using the names shown above. This manifest records checksums; it does not send files or verify who created them.", 14), spaced())
        val samplePanel = column().apply { visibility = View.GONE }
        page.addView(action("Generated workload", View.generateViewId(), false) {
            samplePanel.visibility = if (samplePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, spaced())
        page.addView(samplePanel)
        samplePanel.addView(text("Generated workload", 20, Palette.ink, true), spaced())
        samplePanel.addView(text("Run a small generated sample without selecting files.", 14))
        sample = action("Run generated sample", R.id.run_generated, false) { controller.runGenerated() }
        samplePanel.addView(sample, spaced())
        sampleResult = text("", 14).apply { setTextIsSelectable(true) }
        samplePanel.addView(sampleResult, spaced())
        historyText = text("", 14).apply { visibility = View.GONE }
        page.addView(action("This session only · history", View.generateViewId(), false) {
            historyText.visibility = if (historyText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, spaced())
        page.addView(historyText, spaced())
        setContentView(scroll)
        render()
    }

    private fun render() {
        if (destroyed || !::status.isInitialized) return
        if (exportBytes != null && (!controller.exportReady || exportToken != controller.exportToken)) exportBytes = null
        status.text = controller.message
        historyText.text = "Clears when this screen restarts. History does not restore jobs or enable export.\n\n${controller.history}"
        if (wasBusy && !controller.busy) status.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED)
        wasBusy = controller.busy
        sampleResult.text = controller.generatedResult
        select.isEnabled = !controller.busy && !exporting
        prepare.isEnabled = !controller.busy && !exporting && controller.aliases.isNotEmpty()
        cancel.visibility = if (controller.busy) View.VISIBLE else View.GONE
        export.isEnabled = controller.exportReady && !exporting
        sample.isEnabled = !controller.busy && !exporting
        progress.visibility = if (controller.busy || exporting) View.VISIBLE else View.GONE
        files.removeAllViews()
        controller.aliases.forEachIndexed { index, alias ->
            files.addView(text("${index + 1}.  $alias\nFrom: ${controller.displayNames.getOrNull(index).orEmpty()}", 16, Palette.ink, true).apply {
                textDirection = View.TEXT_DIRECTION_LTR
                background = shape(Color.WHITE)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                contentDescription = "Attachment ${index + 1}. ${controller.displayNames.getOrNull(index).orEmpty()}. Transfer filename $alias"
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun pick() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, PICK)
    }

    private fun requestExport() {
        if (!controller.exportReady || exporting) return
        exportBytes = controller.manifest()
        exportToken = controller.exportToken
        exporting = true
        render()
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "inspection.fieldpack.json")
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }, EXPORT)
    }

    @Deprecated("Activity result API keeps this sample dependency-light")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK && resultCode == RESULT_OK) {
            val selected = data?.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } }
                ?: listOfNotNull(data?.data)
            controller.select(selected)
        }
        if (requestCode == PICK && resultCode != RESULT_OK) {
            controller.exportMessage("Selection cancelled. Your current selection is unchanged.")
        }
        if (requestCode == EXPORT) {
            val uri = data?.data
            val bytes = exportBytes?.takeIf { controller.exportReady && exportToken == controller.exportToken }
            exportBytes = null
            if (bytes == null) {
                exporting = false
                controller.exportMessage("This attempt is no longer available for export. Prepare the pack again.")
            } else if (resultCode != RESULT_OK || uri == null) {
                exporting = false
                controller.exportMessage("Export cancelled. Your prepared manifest is ready when you are.")
            } else writeExport(uri, bytes)
        }
    }

    private fun writeExport(uri: Uri, bytes: ByteArray) {
        controller.exportMessage("Saving manifest…")
        writes.execute {
            val result = runCatching {
                val output = contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
                output.use { it.write(bytes); it.flush() }
            }
            runOnUiThread {
                if (!destroyed) {
                    exporting = false
                    controller.exportMessage(if (result.isSuccess) "Manifest exported. Transfer your attachments separately with the listed names."
                    else "Export failed. The destination may contain an incomplete file. Choose a new destination and try again.")
                }
            }
        }
    }

    private fun action(label: String, viewId: Int, primary: Boolean = true, click: () -> Unit) = Button(this).apply {
        id = viewId; text = label; contentDescription = label; isAllCaps = false
        textSize = 16f; minHeight = dp(48)
        setTextColor(if (primary) Color.WHITE else Palette.emerald)
        backgroundTintList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(Palette.border, if (primary) Palette.emerald else Color.WHITE))
        setOnClickListener { click() }
    }
    private fun text(value: String, size: Int, color: Int = Palette.muted, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        setLineSpacing(dp(3).toFloat(), 1f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
    private fun spaced() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        destroyed = true
        controller.close()
        exportBytes = null
        writes.shutdown()
        super.onDestroy()
    }
    companion object { private const val PICK = 10; private const val EXPORT = 11 }
}

private object Palette {
    val navy = Color.rgb(16, 44, 58)
    val emerald = Color.rgb(0, 107, 82)
    val background = Color.rgb(243, 246, 247)
    val ink = Color.rgb(23, 51, 64)
    val muted = Color.rgb(77, 100, 112)
    val border = Color.rgb(198, 211, 216)
}
