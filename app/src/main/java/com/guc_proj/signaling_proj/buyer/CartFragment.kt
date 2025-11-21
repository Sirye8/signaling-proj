package com.guc_proj.signaling_proj.buyer

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.BuyerHomeActivity
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.User
import com.guc_proj.signaling_proj.databinding.FragmentCartBinding
import java.util.*
import java.util.Locale

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var cartAdapter: CartAdapter
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

    // Address Management
    private val userAddresses = mutableListOf<String>()
    private val addressLabels = mutableListOf<String>()
    private lateinit var addressAdapter: ArrayAdapter<String>

    private var selectedDeliveryAddress: String? = null
    private var calculatedDeliveryFee: Double = 0.0

    // --- FEE CONSTANTS ---
    private val FEE_HIGH = 15.0
    private val FEE_MED = 10.0
    private val FEE_LOW = 5.0
    private val THRESHOLD_SMALL = 30.0
    private val THRESHOLD_MEDIUM = 70.0
    private val THRESHOLD_LARGE = 150.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        setupAddressSpinner()

        // UI Listeners
        binding.deliveryRadioGroup.setOnCheckedChangeListener { _, _ ->
            recalculateTotals()
        }

        binding.addNewAddressButton.setOnClickListener { showAddAddressDialog() }

        binding.addMoreItemsButton.setOnClickListener { navigateToAddMoreItems() }
        binding.placeOrderButton.setOnClickListener { attemptPlaceOrder() }
        binding.clearCartButton.setOnClickListener {
            CartManager.clearCart()
            updateCartView()
        }

        // Initial Load
        updateCartView()
        fetchUserAddresses()
    }

    private fun setupAddressSpinner() {
        addressAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, addressLabels)
        binding.addressSpinner.adapter = addressAdapter

        binding.addressSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (userAddresses.isNotEmpty()) {
                    selectedDeliveryAddress = userAddresses[position]
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun fetchUserAddresses() {
        val uid = auth.currentUser?.uid ?: return

        database.child("Users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userAddresses.clear()
                addressLabels.clear()

                // 1. Get Main Address (Profile)
                val userProfile = snapshot.getValue(User::class.java)
                val mainAddress = userProfile?.address

                // Ensure main address is first in the list
                if (!mainAddress.isNullOrEmpty()) {
                    userAddresses.add(mainAddress)
                    addressLabels.add("Main: $mainAddress")
                }

                // 2. Get Saved Addresses from List
                val savedNode = snapshot.child("savedAddresses")
                for (child in savedNode.children) {
                    val addr = child.getValue(String::class.java)
                    if (!addr.isNullOrEmpty() && addr != mainAddress) {
                        userAddresses.add(addr)
                        addressLabels.add(addr)
                    }
                }

                if (_binding != null) {
                    addressAdapter.notifyDataSetChanged()

                    if (userAddresses.isNotEmpty()) {
                        binding.addressSpinner.setSelection(0)
                        selectedDeliveryAddress = userAddresses[0]
                    } else {
                        selectedDeliveryAddress = null
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddAddressDialog() {
        val input = EditText(requireContext())
        input.hint = "City, Street, Building No"
        AlertDialog.Builder(requireContext())
            .setTitle("Add New Address")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) saveNewAddress(txt)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveNewAddress(address: String) {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("savedAddresses").push().setValue(address)
            .addOnSuccessListener {
                Toast.makeText(context, "Address added!", Toast.LENGTH_SHORT).show()
                fetchUserAddresses() // Refresh list
            }
    }

    private fun recalculateTotals() {
        if (_binding == null) return

        val isDelivery = binding.radioDelivery.isChecked
        val subtotal = CartManager.getCartTotal()

        if (isDelivery) {
            binding.deliveryAddressSection.visibility = View.VISIBLE
            binding.deliveryFeeLayout.visibility = View.VISIBLE

            // Inverse Tiered Fee
            calculatedDeliveryFee = when {
                subtotal < THRESHOLD_SMALL -> FEE_HIGH
                subtotal < THRESHOLD_MEDIUM -> FEE_MED
                subtotal < THRESHOLD_LARGE -> FEE_LOW
                else -> 0.0
            }

            if (calculatedDeliveryFee == 0.0) {
                binding.deliveryFeeTextView.text = "FREE"
                binding.deliveryFeeTextView.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            } else {
                binding.deliveryFeeTextView.text = String.format(Locale.US, "$%.2f", calculatedDeliveryFee)
                binding.deliveryFeeTextView.setTextColor(binding.totalPriceTextView.currentTextColor)
            }
        } else {
            // Pick-up
            binding.deliveryAddressSection.visibility = View.GONE
            binding.deliveryFeeLayout.visibility = View.GONE
            calculatedDeliveryFee = 0.0
        }

        val finalTotal = subtotal + calculatedDeliveryFee
        binding.subtotalTextView.text = String.format(Locale.US, "$%.2f", subtotal)
        binding.totalPriceTextView.text = String.format(Locale.US, "$%.2f", finalTotal)
    }

    private fun setupRecyclerView() {
        cartAdapter = CartAdapter(
            CartManager.getCartItems(),
            { cartItem ->
                cartItem.product?.productId?.let { CartManager.updateQuantity(it, cartItem.quantityInCart) }
                updateCartView()
            },
            { cartItem ->
                cartItem.product?.productId?.let { CartManager.removeItem(it) }
                updateCartView()
            }
        )
        binding.cartRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = cartAdapter
        }
    }

    private fun updateCartView() {
        val cartItems = CartManager.getCartItems()
        cartAdapter.updateItems(cartItems)

        if (_binding == null) return
        if (cartItems.isEmpty()) {
            binding.emptyCartView.visibility = View.VISIBLE
            binding.cartContentScroll.visibility = View.GONE
            binding.totalsContainer.visibility = View.GONE
        } else {
            binding.emptyCartView.visibility = View.GONE
            binding.cartContentScroll.visibility = View.VISIBLE
            binding.totalsContainer.visibility = View.VISIBLE
            recalculateTotals()
        }
    }

    // ---------------------------------------------------------
    // NEW ROBUST ORDER PROCESSING LOGIC (Two-Phase Commit)
    // ---------------------------------------------------------

    private fun attemptPlaceOrder() {
        val isDelivery = binding.radioDelivery.isChecked
        if (isDelivery && selectedDeliveryAddress.isNullOrEmpty()) {
            Toast.makeText(context, "Please select a delivery address.", Toast.LENGTH_SHORT).show()
            return
        }

        val sellerId = CartManager.getSellerId()
        if (sellerId == null || CartManager.getCartItems().isEmpty()) {
            Toast.makeText(context, "Cart is empty", Toast.LENGTH_SHORT).show()
            return
        }

        binding.placeOrderButton.isEnabled = false
        Toast.makeText(context, "Processing order...", Toast.LENGTH_SHORT).show()

        val itemsToBuy = CartManager.getCartItems()
        // Start the chain with the first item
        reserveNextItem(itemsToBuy, 0, mutableListOf())
    }

    /**
     * Recursively tries to reserve stock for each item.
     * If successful, it moves to the next.
     * If it fails, it triggers a rollback.
     */
    private fun reserveNextItem(
        items: List<com.guc_proj.signaling_proj.CartItem>,
        index: Int,
        reservedItems: MutableList<com.guc_proj.signaling_proj.CartItem>
    ) {
        // BASE CASE: If we have processed all items successfully
        if (index >= items.size) {
            // All stock reserved! Finalize the order.
            finalizeOrder()
            return
        }

        val currentItem = items[index]
        val productId = currentItem.product?.productId ?: return
        val quantityNeeded = currentItem.quantityInCart

        val productRef = FirebaseDatabase.getInstance().getReference("Products").child(productId).child("quantity")

        productRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val stock = currentData.getValue(Int::class.java) ?: return Transaction.success(currentData)

                // CRITICAL CHECK: Do we have enough stock?
                if (stock < quantityNeeded) {
                    // Abort this specific transaction
                    return Transaction.abort()
                }

                // Decrement stock
                currentData.value = stock - quantityNeeded
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    // Success: Add to reserved list and try the next item
                    reservedItems.add(currentItem)
                    reserveNextItem(items, index + 1, reservedItems)
                } else {
                    // Failure: Out of stock or race condition lost.
                    // ROLLBACK everything we reserved so far.
                    if (_binding != null && context != null) {
                        val failedProductName = currentItem.product?.name ?: "Item"

                        // --- UX IMPROVEMENT: Alert Dialog instead of Toast ---
                        AlertDialog.Builder(requireContext())
                            .setTitle("Order Failed")
                            .setMessage("Unfortunately, '$failedProductName' is out of stock or insufficient quantity available.")
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show()

                        rollbackStock(reservedItems)
                    }
                }
            }
        })
    }

    /**
     * If an order fails halfway, this restores the stock for items we previously grabbed.
     */
    private fun rollbackStock(itemsToRestore: List<com.guc_proj.signaling_proj.CartItem>) {
        val productsRef = FirebaseDatabase.getInstance().getReference("Products")

        itemsToRestore.forEach { item ->
            val productId = item.product?.productId ?: return@forEach
            val quantityToRestore = item.quantityInCart

            productsRef.child(productId).child("quantity").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentQty = currentData.getValue(Int::class.java) ?: return Transaction.success(currentData)
                    // Add the stock back
                    currentData.value = currentQty + quantityToRestore
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    // Rollback complete for this item
                }
            })
        }

        // Re-enable button so user can fix cart and try again
        if (_binding != null) {
            binding.placeOrderButton.isEnabled = true
        }
    }

    /**
     * Called ONLY when all items are successfully reserved.
     */
    private fun finalizeOrder() {
        val buyerId = auth.currentUser?.uid ?: return
        val itemsMap = CartManager.getCartItemsMap()
        val sellerId = CartManager.getSellerId()
        val finalTotal = CartManager.getCartTotal() + calculatedDeliveryFee

        if (sellerId == null) return

        // Fetch Names and Save Order
        database.child("Users").child(buyerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(buyerSnap: DataSnapshot) {
                val buyerName = buyerSnap.getValue(User::class.java)?.name ?: "Unknown"

                database.child("Users").child(sellerId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(sellerSnap: DataSnapshot) {
                        val sellerName = sellerSnap.getValue(User::class.java)?.name ?: "Unknown"

                        val orderId = database.child("Orders").push().key ?: UUID.randomUUID().toString()
                        val order = Order(
                            orderId = orderId,
                            buyerId = buyerId,
                            sellerId = sellerId,
                            buyerName = buyerName,
                            sellerName = sellerName,
                            items = itemsMap,
                            totalPrice = finalTotal,
                            status = Order.STATUS_PENDING,
                            deliveryType = if (binding.radioDelivery.isChecked) Order.TYPE_DELIVERY else Order.TYPE_PICKUP,
                            deliveryAddress = if (binding.radioDelivery.isChecked) selectedDeliveryAddress else null,
                            deliveryFee = calculatedDeliveryFee
                        )

                        saveOrderToFirebase(orderId, order)
                    }
                    override fun onCancelled(e: DatabaseError) {
                        if (_binding != null) binding.placeOrderButton.isEnabled = true
                    }
                })
            }
            override fun onCancelled(e: DatabaseError) {
                if (_binding != null) binding.placeOrderButton.isEnabled = true
            }
        })
    }

    private fun saveOrderToFirebase(orderId: String, order: Order) {
        database.child("Orders").child(orderId).setValue(order)
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(context, "Order placed successfully!", Toast.LENGTH_SHORT).show()
                CartManager.clearCart()
                updateCartView()
                binding.placeOrderButton.isEnabled = true
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(context, "Failed to place order: ${it.message}", Toast.LENGTH_SHORT).show()
                binding.placeOrderButton.isEnabled = true
            }
    }

    private fun navigateToAddMoreItems() {
        val currentSellerId = CartManager.getSellerId()
        if (currentSellerId != null) {
            val intent = Intent(activity, com.guc_proj.signaling_proj.buyer.ShopProductsActivity::class.java)
            intent.putExtra("SELLER_ID", currentSellerId)
            startActivity(intent)
        } else {
            (activity as? BuyerHomeActivity)?.findViewById<ViewPager2>(com.guc_proj.signaling_proj.R.id.buyer_view_pager)?.currentItem = 0
        }
    }

    override fun onResume() {
        super.onResume()
        updateCartView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}