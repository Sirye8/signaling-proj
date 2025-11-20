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

    private var sellerAddress: String? = null
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
        fetchSellerAddress()
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

    private fun fetchSellerAddress() {
        val sellerId = CartManager.getSellerId() ?: return
        // We fetch this for the record, but we no longer block the order if it's missing.
        database.child("Users").child(sellerId).child("address").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                sellerAddress = snapshot.getValue(String::class.java)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
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

                    // --- FIX: Immediately set the selected address if list is not empty ---
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

    private fun attemptPlaceOrder() {
        val isDelivery = binding.radioDelivery.isChecked

        // --- FIX: Removed Seller Address Validation Toast ---
        // We no longer check if sellerAddress is null.

        // 2. Validate User Address (Only if Delivery is chosen)
        if (isDelivery && selectedDeliveryAddress.isNullOrEmpty()) {
            Toast.makeText(context, "Please select a delivery address.", Toast.LENGTH_SHORT).show()
            return
        }

        placeOrder()
    }

    private fun placeOrder() {
        val buyerId = auth.currentUser?.uid ?: return
        val items = CartManager.getCartItemsMap()
        val sellerId = CartManager.getSellerId()
        val finalTotal = CartManager.getCartTotal() + calculatedDeliveryFee

        if (items.isEmpty() || sellerId == null) return

        binding.placeOrderButton.isEnabled = false
        Toast.makeText(context, "Placing order...", Toast.LENGTH_SHORT).show()

        // Decrement Stock
        decrementStockForOrder(items)

        // Fetch Names
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
                            items = items,
                            totalPrice = finalTotal,
                            status = Order.STATUS_PENDING,

                            // Delivery Info
                            deliveryType = if (binding.radioDelivery.isChecked) Order.TYPE_DELIVERY else Order.TYPE_PICKUP,
                            deliveryAddress = if (binding.radioDelivery.isChecked) selectedDeliveryAddress else null,
                            deliveryFee = calculatedDeliveryFee
                        )

                        saveOrderToFirebase(orderId, order)
                    }
                    override fun onCancelled(e: DatabaseError) { binding.placeOrderButton.isEnabled = true }
                })
            }
            override fun onCancelled(e: DatabaseError) { binding.placeOrderButton.isEnabled = true }
        })
    }

    private fun decrementStockForOrder(items: Map<String, com.guc_proj.signaling_proj.CartItem>) {
        val productsRef = FirebaseDatabase.getInstance().getReference("Products")
        items.forEach { (productId, cartItem) ->
            val quantityToReduce = cartItem.quantityInCart
            productsRef.child(productId).child("quantity").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentQty = currentData.getValue(Int::class.java) ?: return Transaction.success(currentData)
                    currentData.value = if (currentQty - quantityToReduce < 0) 0 else currentQty - quantityToReduce
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
        }
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
                Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
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