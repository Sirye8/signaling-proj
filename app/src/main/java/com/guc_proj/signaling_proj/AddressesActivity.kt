package com.guc_proj.signaling_proj

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.databinding.ActivityAddressesBinding

class AddressesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddressesBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var adapter: AddressAdapter

    private val addressList = mutableListOf<Pair<String, AddressItem>>()
    private var editingKey: String? = null

    private val addressFormLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                // Construct Object from Fields
                val newItem = AddressItem(
                    name = data.getStringExtra("EXTRA_NAME"),
                    city = data.getStringExtra("EXTRA_CITY"),
                    street = data.getStringExtra("EXTRA_STREET"),
                    building = data.getStringExtra("EXTRA_BUILDING"),
                    floor = data.getStringExtra("EXTRA_FLOOR"),
                    apartment = data.getStringExtra("EXTRA_APT"),
                    instructions = data.getStringExtra("EXTRA_INSTRUCTIONS")
                )

                if (editingKey != null) {
                    updateAddress(editingKey!!, newItem)
                } else {
                    addNewAddress(newItem)
                }
            }
        }
        editingKey = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddressesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return finish()
        database = FirebaseDatabase.getInstance().getReference("Users").child(uid).child("Addresses")

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        fetchAddresses()

        binding.fabAddAddress.setOnClickListener {
            editingKey = null
            val intent = Intent(this, AddressFormActivity::class.java)
            addressFormLauncher.launch(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = AddressAdapter(addressList,
            onEdit = { key, item ->
                editingKey = key
                val intent = Intent(this, AddressFormActivity::class.java).apply {
                    putExtra("EXTRA_NAME", item.name)
                    putExtra("EXTRA_CITY", item.city)
                    putExtra("EXTRA_STREET", item.street)
                    putExtra("EXTRA_BUILDING", item.building)
                    putExtra("EXTRA_FLOOR", item.floor)
                    putExtra("EXTRA_APT", item.apartment)
                    putExtra("EXTRA_INSTRUCTIONS", item.instructions)
                }
                addressFormLauncher.launch(intent)
            },
            onDelete = { key -> showDeleteDialog(key) }
        )
        binding.addressesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.addressesRecyclerView.adapter = adapter
    }

    private fun fetchAddresses() {
        binding.loadingIndicator.visibility = View.VISIBLE
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                addressList.clear()
                for (child in snapshot.children) {
                    val item = child.getValue(AddressItem::class.java)
                    val key = child.key
                    if (item != null && key != null) {
                        addressList.add(key to item)
                    }
                }
                adapter.notifyDataSetChanged()
                binding.loadingIndicator.visibility = View.GONE
                binding.emptyView.visibility = if (addressList.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun onCancelled(error: DatabaseError) {
                binding.loadingIndicator.visibility = View.GONE
            }
        })
    }

    private fun addNewAddress(item: AddressItem) {
        database.push().setValue(item).addOnSuccessListener { Toast.makeText(this, "Address added", Toast.LENGTH_SHORT).show() }
    }

    private fun updateAddress(key: String, item: AddressItem) {
        database.child(key).setValue(item).addOnSuccessListener { Toast.makeText(this, "Address updated", Toast.LENGTH_SHORT).show() }
    }

    private fun showDeleteDialog(key: String) {
        AlertDialog.Builder(this).setTitle("Delete Address").setMessage("Are you sure?")
            .setPositiveButton("Delete") { _, _ -> database.child(key).removeValue() }
            .setNegativeButton("Cancel", null).show()
    }

    class AddressAdapter(
        private val list: List<Pair<String, AddressItem>>,
        private val onEdit: (String, AddressItem) -> Unit,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<AddressAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.nameTextView)
            val addrText: TextView = view.findViewById(R.id.addressTextView)
            val edit: Button = view.findViewById(R.id.editButton)
            val delete: Button = view.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_address_manage, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (key, item) = list[position]
            holder.nameText.text = item.name
            // Use helper to show formatted string
            holder.addrText.text = item.toFormattedString()

            holder.edit.setOnClickListener { onEdit(key, item) }
            holder.delete.setOnClickListener { onDelete(key) }
        }

        override fun getItemCount() = list.size
    }
}