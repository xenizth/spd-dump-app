package com.rynvortex.spddump.ui

import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.rynvortex.spddump.FilesStore
import com.rynvortex.spddump.databinding.FragmentFilesBinding

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private var showingInput = true
    private lateinit var adapter: FileAdapter

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val name = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
            FilesStore.importToInput(requireContext(), uri, name)
        }
        refreshList()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = FileAdapter(emptyList()) { file ->
            FilesStore.delete(file)
            refreshList()
        }
        binding.filesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.filesRecyclerView.adapter = adapter

        binding.tabInputButton.setOnClickListener { showingInput = true; refreshList() }
        binding.tabBackupButton.setOnClickListener { showingInput = false; refreshList() }
        binding.importButton.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }

        refreshList()
    }

    private fun refreshList() {
        val files = if (showingInput)
            FilesStore.listInputFiles(requireContext())
        else
            FilesStore.listBackupFiles(requireContext())

        adapter.submit(files)
        binding.emptyText.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.filesRecyclerView.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        binding.importButton.visibility = if (showingInput) View.VISIBLE else View.GONE
        binding.hintText.text = if (showingInput)
            "Target: internalfiles/input/ — Import .img / .bin partitions here to flash with SPD BROM or Fastboot."
        else
            "Target: internalfiles/backup/ — Files created by Backup Partition / Backup IMEI & NV land here."
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
