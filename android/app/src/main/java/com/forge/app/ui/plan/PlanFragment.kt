package com.forge.app.ui.plan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.app.databinding.FragmentPlanBinding
import com.forge.app.ui.ForgeViewModelFactory
import com.forge.app.ui.adapters.PlanEntryAdapter

class PlanFragment : Fragment() {

    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PlanViewModel
    private lateinit var planAdapter: PlanEntryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ForgeViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[PlanViewModel::class.java]

        setupRecycler()
        setupInput()
        observeViewModel()
    }

    private fun setupRecycler() {
        planAdapter = PlanEntryAdapter()
        binding.rvPlanEntries.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = planAdapter
        }
    }

    private fun setupInput() {
        binding.btnPlanSend.setOnClickListener {
            submitCommand()
        }

        binding.etPlanInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitCommand()
                true
            } else false
        }
    }

    private fun submitCommand() {
        val text = binding.etPlanInput.text.toString().trim()
        if (text.isNotEmpty()) {
            viewModel.submitCommand(text)
            binding.etPlanInput.text?.clear()
        }
    }

    private fun observeViewModel() {
        viewModel.planEntries.observe(viewLifecycleOwner) { entries ->
            planAdapter.submitList(entries)
            if (entries.isNotEmpty()) {
                binding.rvPlanEntries.postDelayed({
                    binding.rvPlanEntries.smoothScrollToPosition(entries.size - 1)
                }, 100)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
