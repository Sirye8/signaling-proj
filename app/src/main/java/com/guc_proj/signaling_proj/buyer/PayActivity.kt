package com.guc_proj.signaling_proj.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.CardFormActivity
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

    private var editingCardId: String? = null

    private val cardFormLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val number = data.getStringExtra("EXTRA_NUMBER") ?: ""
                val holder = data.getStringExtra("EXTRA_HOLDER") ?: ""
                val expiry = data.getStringExtra("EXTRA_EXPIRY") ?: ""

                // Mask the number
                val masked = if (number.length >= 4) "**** **** **** ${number.takeLast(4)}" else number

                if (editingCardId != null) {
                    // Update existing
                    val updatedCard = PaymentCard(editingCardId, masked, holder, expiry)
                    database.child("Cards").child(editingCardId!!).setValue(updatedCard)
                    Toast.makeText(this, "Card updated", Toast.LENGTH_SHORT).show()
                } else {
                    // Add new
                    val key = database.child("Cards").push().key ?: return@registerForActivityResult
                    val newCard = PaymentCard(key, masked, holder, expiry)
                    database.child("Cards").child(key).setValue(newCard)
                    Toast.makeText(this, "Card added", Toast.LENGTH_SHORT).show()
                }
            }
        }
        editingCardId = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        database = FirebaseDatabase.getInstance().getReference("Users").child(uid)

        setupCardsRecyclerView()
        fetchUserData()
        fetchCards()

        binding.addCardButton.setOnClickListener {
            editingCardId = null
            val intent = Intent(this, CardFormActivity::class.java)
            cardFormLauncher.launch(intent)
        }
    }

    private fun setupCardsRecyclerView() {
        cardsAdapter = CardsAdapter(cardsList,
            onEdit = { card ->
                editingCardId = card.cardId
                val intent = Intent(this, CardFormActivity::class.java)
                // Pass dummy data or store full number securely in real app
                // Here we just pass what we have to prepopulate
                intent.putExtra("EXTRA_NUMBER", "") // Can't edit number in this simple flow usually, or let them re-enter
                intent.putExtra("EXTRA_HOLDER", card.cardHolder)
                intent.putExtra("EXTRA_EXPIRY", card.expiryDate)
                cardFormLauncher.launch(intent)
            },
            onDelete = { card -> deleteCard(card) }
        )
        binding.cardsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.cardsRecyclerView.adapter = cardsAdapter
    }

    private fun fetchUserData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isDestroyed || isFinishing) return
                val user = snapshot.getValue(User::class.java)
                val credit = user?.credit ?: 0.0
                binding.creditBalanceText.text = String.format(Locale.US, "EGP%.2f", credit)
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

    private fun deleteCard(card: PaymentCard) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Card")
            .setMessage("Are you sure you want to remove this payment method?")
            .setIcon(R.drawable.ic_delete)
            .setPositiveButton("Remove") { _, _ ->
                card.cardId?.let {
                    database.child("Cards").child(it).removeValue()
                    Toast.makeText(this, "Card removed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class CardsAdapter(
        private val list: List<PaymentCard>,
        private val onEdit: (PaymentCard) -> Unit,
        private val onDelete: (PaymentCard) -> Unit
    ) : RecyclerView.Adapter<CardsAdapter.Holder>() {
        class Holder(val view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.cardNumberText)
            val holder: TextView = view.findViewById(R.id.cardHolderText)
            val edit: ImageButton = view.findViewById(R.id.editCardButton)
            val delete: ImageButton = view.findViewById(R.id.deleteCardButton)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_payment_card, parent, false)
            return Holder(v)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = list[position]
            holder.number.text = item.cardNumber
            holder.holder.text = item.cardHolder
            holder.edit.setOnClickListener { onEdit(item) }
            holder.delete.setOnClickListener { onDelete(item) }
        }
        override fun getItemCount() = list.size
    }
}