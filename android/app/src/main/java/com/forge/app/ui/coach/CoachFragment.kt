package com.forge.app.ui.coach

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
import java.util.Calendar

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

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            messageAdapter.submitList(messages)
            if (messages.isNotEmpty()) {
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
