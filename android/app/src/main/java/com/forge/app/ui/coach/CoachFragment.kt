package com.forge.app.ui.coach

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.app.R
import com.forge.app.databinding.FragmentCoachBinding
import com.forge.app.ui.ForgeViewModelFactory
import com.forge.app.ui.adapters.CoachMessageAdapter

class CoachFragment : Fragment() {

    private var _binding: FragmentCoachBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CoachViewModel
    private lateinit var messageAdapter: CoachMessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ForgeViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[CoachViewModel::class.java]

        setupRecycler()
        setupInput()
        setupChips()
        setupClearChat()
        setupKeyButton()
        observeViewModel()
    }

    private fun setupRecycler() {
        messageAdapter = CoachMessageAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun setupInput() {
        binding.btnCoachSend.setOnClickListener {
            sendMessage()
        }

        binding.etCoachInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
    }

    private fun sendMessage() {
        val text = binding.etCoachInput.text.toString().trim()
        if (text.isNotEmpty()) {
            viewModel.sendMessage(text)
            binding.etCoachInput.text?.clear()
        }
    }

    private fun setupChips() {
        viewModel.suggestionChips.observe(viewLifecycleOwner) { chips ->
            binding.llChips.removeAllViews()
            chips.forEach { chipText ->
                val chip = layoutInflater.inflate(R.layout.item_chip, binding.llChips, false) as TextView
                chip.text = chipText
                                chip.setOnClickListener {
                    viewModel.sendMessage(chipText)
                }
                binding.llChips.addView(chip)
            }
        }
    }

    private fun setupKeyButton() {
        binding.btnCoachKey.setOnClickListener {
            val context = requireContext()
            val input = android.widget.EditText(context).apply {
                hint = "Paste your Gemini API key"
                setText(com.forge.app.util.Gemini.savedKey(context))
                setTextColor(android.graphics.Color.parseColor("#F5F5F5"))
                setHintTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                setBackgroundResource(R.drawable.bg_input)
                setPadding(36, 28, 36, 28)
                setSingleLine(true)
            }
            val box = android.widget.FrameLayout(context).apply {
                setPadding(48, 24, 48, 0)
                addView(input)
            }
            AlertDialog.Builder(context, R.style.ForgeDialogTheme)
                .setTitle("Coach API key")
                .setMessage("Leave empty to use the built-in key.")
                .setView(box)
                .setPositiveButton("Save") { _, _ ->
                    com.forge.app.util.Gemini.saveKey(context, input.text.toString())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupClearChat() {
        binding.btnClearChat.setOnClickListener {
            AlertDialog.Builder(requireContext(), R.style.ForgeDialogTheme)
                .setTitle("Clear chat")
                .setMessage("Delete all messages? This cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clearChat()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            messageAdapter.submitList(messages)

            // Show/hide empty state
            val isEmpty = messages.isNullOrEmpty()
            binding.llEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvMessages.visibility = if (isEmpty) View.GONE else View.VISIBLE

            if (!isEmpty) {
                binding.rvMessages.postDelayed({
                    binding.rvMessages.smoothScrollToPosition(messages.size - 1)
                }, 100)
            }
        }

        viewModel.isTyping.observe(viewLifecycleOwner) { typing ->
            binding.tvTypingIndicator.visibility = if (typing) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
