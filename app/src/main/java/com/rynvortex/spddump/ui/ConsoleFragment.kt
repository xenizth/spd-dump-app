package com.rynvortex.spddump.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rynvortex.spddump.FlashSession
import com.rynvortex.spddump.databinding.FragmentConsoleBinding

class ConsoleFragment : Fragment() {

    private var _binding: FragmentConsoleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsoleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        FlashSession.logLines.observe(viewLifecycleOwner) { lines ->
            binding.consoleLogText.text = lines.joinToString("\n")
            binding.logScroll.post { binding.logScroll.fullScroll(View.FOCUS_DOWN) }
        }

        binding.runButton.setOnClickListener {
            val text = binding.commandInput.text.toString().trim()
            if (text.isNotEmpty()) {
                FlashSession.runArgs(text.split(Regex("\\s+")))
                binding.commandInput.text.clear()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
