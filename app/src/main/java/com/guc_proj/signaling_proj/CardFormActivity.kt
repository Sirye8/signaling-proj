package com.guc_proj.signaling_proj

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.guc_proj.signaling_proj.databinding.ActivityCardFormBinding

class CardFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCardFormBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Check if we are in Edit Mode
        if (intent.hasExtra("EXTRA_NUMBER")) {
            binding.cardNumberEditText.setText(intent.getStringExtra("EXTRA_NUMBER"))
            binding.cardHolderEditText.setText(intent.getStringExtra("EXTRA_HOLDER"))
            binding.expiryEditText.setText(intent.getStringExtra("EXTRA_EXPIRY"))
            binding.saveCardButton.text = "Update Card"
            binding.toolbar.title = "Edit Card"
        }

        binding.saveCardButton.setOnClickListener {
            if (validateInputs()) {
                val resultIntent = Intent().apply {
                    putExtra("EXTRA_NUMBER", binding.cardNumberEditText.text.toString().trim())
                    putExtra("EXTRA_HOLDER", binding.cardHolderEditText.text.toString().trim())
                    putExtra("EXTRA_EXPIRY", binding.expiryEditText.text.toString().trim())
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val number = binding.cardNumberEditText.text.toString().trim()
        val holder = binding.cardHolderEditText.text.toString().trim()
        val expiry = binding.expiryEditText.text.toString().trim()

        if (number.length < 16) {
            binding.cardNumberInputLayout.error = "Invalid Card Number (16 digits required)"
            return false
        }
        if (holder.isEmpty()) {
            binding.cardHolderInputLayout.error = "Holder Name Required"
            return false
        }
        if (expiry.isEmpty()) {
            binding.expiryInputLayout.error = "Expiry Required"
            return false
        }
        return true
    }
}