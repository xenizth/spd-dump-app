package com.rynvortex.spddump.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.rynvortex.spddump.FlashSession
import com.rynvortex.spddump.FilesStore
import com.rynvortex.spddump.databinding.FragmentOperationsBinding

class OperationsFragment : Fragment() {

    private var _binding: FragmentOperationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOperationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.operationsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.operationsRecyclerView.adapter = OperationAdapter(OperationCatalog.all) { op ->
            onOperationTapped(op)
        }
    }

    private fun onOperationTapped(op: Operation) {
        if (op.id == "custom_cli") {
            showCustomCliDialog()
            return
        }
        if (op.confirmMessage != null) {
            AlertDialog.Builder(requireContext())
                .setTitle(op.title)
                .setMessage(op.confirmMessage)
                .setPositiveButton("Continue") { _, _ -> resolveAndRun(op) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            resolveAndRun(op)
        }
    }

    private fun resolveAndRun(op: Operation) {
        when (op.requiresFile) {
            FileRequirement.NONE -> FlashSession.runArgs(op.argsTemplate)
            FileRequirement.SINGLE_IMAGE -> pickFromInputFiles(op)
            FileRequirement.XML -> pickXmlAndRun(op)
        }
    }

    /** Lets the user choose which imported file fills {input}/{backup} before running. */
    private fun pickFromInputFiles(op: Operation) {
        val files = FilesStore.listInputFiles(requireContext()) + FilesStore.listBackupFiles(requireContext())
        if (files.isEmpty()) {
            FlashSession.appendLog("No files available - import one from the Files tab first")
            return
        }
        val names = files.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select file for ${op.title}")
            .setItems(names) { _, which ->
                val filled = op.argsTemplate.map { arg ->
                    arg.replace("{input}", files[which].absolutePath)
                        .replace("{backup}", files[which].absolutePath)
                }
                FlashSession.runArgs(filled)
            }
            .show()
    }

    private fun pickXmlAndRun(op: Operation) {
        val files = FilesStore.listInputFiles(requireContext()).filter { it.name.endsWith(".xml") }
        if (files.isEmpty()) {
            FlashSession.appendLog("No .xml partition table found in Input Files")
            return
        }
        val names = files.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select partition table XML")
            .setItems(names) { _, which ->
                val filled = op.argsTemplate.map { it.replace("{xml}", files[which].absolutePath) }
                FlashSession.runArgs(filled)
            }
            .show()
    }

    private fun showCustomCliDialog() {
        val input = EditText(requireContext())
        input.hint = "e.g. read_flash boot 0 0x2000000 boot.img"
        AlertDialog.Builder(requireContext())
            .setTitle("Custom spd_dump command")
            .setView(input)
            .setPositiveButton("Run") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) FlashSession.runArgs(text.split(Regex("\\s+")))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
