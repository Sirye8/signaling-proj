package com.guc_proj.signaling_proj.buyer

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.PaymentCard
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.User
import com.guc_proj.signaling_proj.databinding.ActivityPayBinding
import java.util.Locale

class PayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPayBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private val cardsList = mutableListOf<PaymentCard>()
    private lateinit var cardsAdapter: CardsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        database = FirebaseDatabase.getInstance().getReference("Users").child(uid)

        setupCardsRecyclerView()
        fetchUserData()
        fetchCards()

        binding.addCardButton.setOnClickListener { showAddCardDialog() }
    }

    private fun setupCardsRecyclerView() {
        cardsAdapter = CardsAdapter(cardsList) { card ->
            deleteCard(card)
        }
        binding.cardsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.cardsRecyclerView.adapter = cardsAdapter
    }

    private fun fetchUserData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isDestroyed || isFinishing) return
                val user = snapshot.getValue(User::class.java)
                val credit = user?.credit ?: 0.0
                binding.creditBalanceText.text = String.format(Locale.US, "$%.2f", credit)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchCards() {
        database.child("Cards").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isDestroyed || isFinishing) return
                cardsList.clear()
                for (child in snapshot.children) {
                    val card = child.getValue(PaymentCard::class.java)
                    card?.let { cardsList.add(it) }
                }
                cardsAdapter.notifyDataSetChanged()
                binding.emptyCardsView.visibility = if (cardsList.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddCardDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_card, null)
        val numInput = dialogView.findViewById<EditText>(R.id.cardNumberInput)
        val holderInput = dialogView.findViewById<EditText>(R.id.cardHolderInput)
        val expiryInput = dialogView.findViewById<EditText>(R.id.expiryInput)

        AlertDialog.Builder(this)
            .setTitle("Add New Card")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val number = numInput.text.toString().trim()
                val holder = holderInput.text.toString().trim()
                val expiry = expiryInput.text.toString().trim()

                if (number.length >= 16 && holder.isNotEmpty() && expiry.isNotEmpty()) {
                    val cardId = database.child("Cards").push().key ?: return@setPositiveButton
                    val masked = "**** **** **** ${number.takeLast(4)}"
                    val card = PaymentCard(cardId, masked, holder, expiry)
                    database.child("Cards").child(cardId).setValue(card)
                } else {
                    Toast.makeText(this, "Invalid card details", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCard(card: PaymentCard) {
        card.cardId?.let {
            database.child("Cards").child(it).removeValue()
            Toast.makeText(this, "Card removed", Toast.LENGTH_SHORT).show()
        }
    }

    // Internal Adapter Class
    class CardsAdapter(
        private val list: List<PaymentCard>,
        private val onDelete: (PaymentCard) -> Unit
    ) : RecyclerView.Adapter<CardsAdapter.Holder>() {
        class Holder(val view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.cardNumberText)
            val holder: TextView = view.findViewById(R.id.cardHolderText)
            val delete: View = view.findViewById(R.id.deleteCardButton)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_payment_card, parent, false)
            return Holder(v)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            holder.number.text = item.cardNumber
            holder.holder.text = item.cardHolder
            holder.delete.setOnClickListener { onDelete(item) }
        }
        override fun getItemCount() = list.size
    }
}