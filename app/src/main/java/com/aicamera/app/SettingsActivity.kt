package com.aicamera.app

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.aicamera.app.ai.AIProvider
import com.aicamera.app.ai.AISettings
import com.aicamera.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val providers = AIProvider.values()
        binding.providerSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            providers.map { it.displayName }
        )

        val currentProvider = AISettings.getProvider(this)
        binding.providerSpinner.setSelection(providers.indexOf(currentProvider))
        binding.apiKeyInput.setText(AISettings.getApiKey(this))
        binding.customEndpointInput.setText(AISettings.getCustomUrl(this))
        binding.freeTierCheckbox.isChecked = AISettings.useFreeTier(this)
        binding.enableAiEditCheckbox.isChecked = AISettings.isAiEditEnabled(this)
        toggleCustomField(currentProvider)

        binding.providerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                toggleCustomField(providers[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnSaveSettings.setOnClickListener {
            val selected = providers[binding.providerSpinner.selectedItemPosition]
            AISettings.save(
                context = this,
                provider = selected,
                apiKey = binding.apiKeyInput.text.toString().trim(),
                customUrl = binding.customEndpointInput.text.toString().trim(),
                useFreeTier = binding.freeTierCheckbox.isChecked,
                enableAiEdit = binding.enableAiEditCheckbox.isChecked
            )
            finish()
        }
    }

    private fun toggleCustomField(provider: AIProvider) {
        binding.customEndpointInput.visibility =
            if (provider == AIProvider.CUSTOM) View.VISIBLE else View.GONE
    }
}
