//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (maintenance / modernization)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// See COPYING for the full licence text.
//
package org.harleydroid

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HarleyDroidLogs : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private val adapter = LogsAdapter(
        onOpen = { openChart(it.file) },
        onShare = { LogExporter.shareFile(this, it.file) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)
        val appBar = findViewById<View>(R.id.logs_app_bar)
        val content = findViewById<View>(R.id.logs_content)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, bottom = bars.bottom, right = bars.right)
            insets
        }
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.logs_name)
        toolbar.post { ClickEffects.strip(toolbar) }

        list = findViewById(R.id.logs_list)
        empty = findViewById(R.id.logs_empty)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        executor.execute {
            val dir = HarleyDroidLogger.logDirectory(this)
            val infos = try {
                LogParser.listLogFiles(dir).map { LogParser.summarize(it) }
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                adapter.submit(infos)
                empty.visibility = if (infos.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (infos.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun openChart(file: File) {
        startActivity(
            Intent(this, HarleyDroidLogChart::class.java)
                .putExtra(HarleyDroidLogChart.EXTRA_PATH, file.absolutePath)
        )
    }

    private fun confirmDelete(info: LogFileInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logs_delete_title)
            .setMessage(getString(R.string.logs_delete_message, info.file.name))
            .setPositiveButton(R.string.logs_delete) { _, _ ->
                if (info.file.delete()) refresh()
                else Toast.makeText(this, R.string.logs_delete_failed, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        suppressPendingTransition(opening = true)
    }

    override fun finish() {
        super.finish()
        suppressPendingTransition(opening = false)
    }

    private fun suppressPendingTransition(opening: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                if (opening) OVERRIDE_TRANSITION_OPEN else OVERRIDE_TRANSITION_CLOSE,
                0,
                0
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private class LogsAdapter(
        private val onOpen: (LogFileInfo) -> Unit,
        private val onShare: (LogFileInfo) -> Unit,
        private val onDelete: (LogFileInfo) -> Unit
    ) : RecyclerView.Adapter<LogsAdapter.Holder>() {

        private val items = ArrayList<LogFileInfo>()
        private val dateFormat =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault())

        fun submit(data: List<LogFileInfo>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log_file, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position], dateFormat, onOpen, onShare, onDelete)
        }

        override fun getItemCount(): Int = items.size

        class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.log_title)
            private val meta: TextView = itemView.findViewById(R.id.log_meta)
            private val share: View = itemView.findViewById(R.id.log_share)
            private val delete: View = itemView.findViewById(R.id.log_delete)

            fun bind(
                info: LogFileInfo,
                dateFormat: DateFormat,
                onOpen: (LogFileInfo) -> Unit,
                onShare: (LogFileInfo) -> Unit,
                onDelete: (LogFileInfo) -> Unit
            ) {
                title.text = info.file.name.removePrefix("harley-").removeSuffix(".log.gz")
                val duration = formatDuration(info.durationMs)
                val size = formatSize(info.sizeBytes)
                val metrics = if (info.presentTypes.isEmpty()) "—"
                else LogMetrics.SELECTABLE.filter { it in info.presentTypes }.joinToString(" · ")
                meta.text = itemView.context.getString(
                    R.string.logs_item_meta,
                    dateFormat.format(Date(info.startMs)),
                    duration,
                    size,
                    metrics
                )
                itemView.setOnClickListener { onOpen(info) }
                share.setOnClickListener { onShare(info) }
                delete.setOnClickListener { onDelete(info) }
            }

            private fun formatDuration(ms: Long): String {
                val sec = (ms / 1000L).coerceAtLeast(0L)
                val h = sec / 3600
                val m = (sec % 3600) / 60
                val s = sec % 60
                return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s)
                else "%d:%02d".format(Locale.US, m, s)
            }

            private fun formatSize(bytes: Long): String {
                if (bytes < 1024) return "$bytes B"
                if (bytes < 1024 * 1024) return "%.1f KB".format(Locale.US, bytes / 1024.0)
                return "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
            }
        }
    }
}
