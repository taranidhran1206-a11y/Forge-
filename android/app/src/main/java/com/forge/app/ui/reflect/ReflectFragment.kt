package com.forge.app.ui.reflect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.app.databinding.FragmentReflectBinding
import com.forge.app.ui.ForgeViewModelFactory
import com.forge.app.ui.adapters.ReflectionAdapter

class ReflectFragment : Fragment() {

    private var _binding: FragmentReflectBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReflectViewModel
    private lateinit var reflectionAdapter: ReflectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReflectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ForgeViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[ReflectViewModel::class.java]

        setupRecycler()
        setupInput()
        observeViewModel()
    }

    private fun setupRecycler() {
        reflectionAdapter = ReflectionAdapter()
        binding.rvReflections.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reflectionAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupInput() {
        binding.btnSubmitReflection.setOnClickListener {
            val text = binding.etReflection.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.submitReflection(text)
                binding.etReflection.text?.clear()
                Toast.makeText(requireContext(), "+15 XP — Reflection logged", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.todayPrompt.observe(viewLifecycleOwner) { prompt ->
            binding.tvReflectPrompt.text = "// $prompt"
        }

        viewModel.reflections.observe(viewLifecycleOwner) { reflections ->
            reflectionAdapter.submitList(reflections)
        }

        viewModel.todayReflection.observe(viewLifecycleOwner) { reflection ->
            if (reflection != null) {
                binding.etReflection.setText(reflection.text)
                binding.btnSubmitReflection.text = "UPDATE"
                binding.etReflection.isEnabled = false
                binding.btnSubmitReflection.isEnabled = false
                binding.tvAlreadyDone.visibility = View.VISIBLE
                binding.tvAlreadyDone.text = "✓ Reflection logged for today"
            } else {
                binding.etReflection.text?.clear()
                binding.btnSubmitReflection.text = "SUBMIT"
                binding.etReflection.isEnabled = true
                binding.btnSubmitReflection.isEnabled = true
                binding.tvAlreadyDone.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
