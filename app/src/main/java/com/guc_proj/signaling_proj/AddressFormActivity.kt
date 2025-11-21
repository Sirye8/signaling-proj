package com.guc_proj.signaling_proj

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.guc_proj.signaling_proj.databinding.ActivityAddressFormBinding

class AddressFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddressFormBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddressFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        if (intent.hasExtra("EXTRA_NAME")) {
            binding.addressNameEditText.setText(intent.getStringExtra("EXTRA_NAME"))
            binding.cityEditText.setText(intent.getStringExtra("EXTRA_CITY"))
            binding.streetEditText.setText(intent.getStringExtra("EXTRA_STREET"))
            binding.buildingEditText.setText(intent.getStringExtra("EXTRA_BUILDING"))
            binding.floorEditText.setText(intent.getStringExtra("EXTRA_FLOOR"))
            binding.aptEditText.setText(intent.getStringExtra("EXTRA_APT"))
            binding.instructionsEditText.setText(intent.getStringExtra("EXTRA_INSTRUCTIONS"))
            binding.confirmAddressButton.text = "Update Address"
        }

        binding.confirmAddressButton.setOnClickListener {
            if (validateInputs()) {
                val resultIntent = Intent().apply {
                    putExtra("EXTRA_NAME", binding.addressNameEditText.text.toString().trim())
                    putExtra("EXTRA_CITY", binding.cityEditText.text.toString().trim())
                    putExtra("EXTRA_STREET", binding.streetEditText.text.toString().trim())
                    putExtra("EXTRA_BUILDING", binding.buildingEditText.text.toString().trim())
                    putExtra("EXTRA_FLOOR", binding.floorEditText.text.toString().trim())
                    putExtra("EXTRA_APT", binding.aptEditText.text.toString().trim())
                    putExtra("EXTRA_INSTRUCTIONS", binding.instructionsEditText.text.toString().trim())
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun validateInputs(): Boolean {
        if (isEmpty(binding.addressNameEditText) ||
            isEmpty(binding.cityEditText) ||
            isEmpty(binding.streetEditText) ||
            isEmpty(binding.buildingEditText)) {
            Toast.makeText(this, "Please fill in the required fields", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun isEmpty(editText: TextInputEditText): Boolean {
        return editText.text.toString().trim().isEmpty()
    }
}