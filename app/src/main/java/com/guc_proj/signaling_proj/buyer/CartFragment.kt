package com.guc_proj.signaling_proj.buyer

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.AddressFormActivity
import com.guc_proj.signaling_proj.AddressItem
import com.guc_proj.signaling_proj.BuyerHomeActivity
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.R
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

    // List of Address Objects
    private val userAddresses = mutableListOf<AddressItem>()

    private var selectedDeliveryAddress: String? = null // Holds the formatted string for the Order
    private var calculatedDeliveryFee: Double = 0.0

    private val FEE_HIGH = 15.0
    private val FEE_MED = 10.0
    private val FEE_LOW = 5.0
    private val THRESHOLD_SMALL = 30.0
    private val THRESHOLD_MEDIUM = 70.0
    private val THRESHOLD_LARGE = 150.0

    private val addressResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val newItem = AddressItem(
                    name = data.getStringExtra("EXTRA_NAME"),
                    city = data.getStringExtra("EXTRA_CITY"),
                    street = data.getStringExtra("EXTRA_STREET"),
                    building = data.getStringExtra("EXTRA_BUILDING"),
                    floor = data.getStringExtra("EXTRA_FLOOR"),
                    apartment = data.getStringExtra("EXTRA_APT"),
                    instructions = data.getStringExtra("EXTRA_INSTRUCTIONS")
                )
                saveNewAddress(newItem)
            }
        }
    }

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
        setupAddressSelector()

        binding.deliveryRadioGroup.setOnCheckedChangeListener { _, _ -> recalculateTotals() }
        binding.addMoreItemsButton.setOnClickListener { navigateToAddMoreItems() }
        binding.placeOrderButton.setOnClickListener { attemptPlaceOrder() }
        binding.clearCartButton.setOnClickListener { CartManager.clearCart(); updateCartView() }

        updateCartView()
        fetchUserAddresses()
    }

    override fun onResume() {
        super.onResume()
        updateCartView()
        fetchUserAddresses()
    }

    private fun setupAddressSelector() {
        binding.addressSelectorCard.setOnClickListener { showAddressBottomSheet() }
    }

    private fun showAddressBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_addresses, null)

        val recyclerView = view.findViewById<RecyclerView>(R.id.sheetAddressRecycler)
        val addButton = view.findViewById<Button>(R.id.sheetAddAddressButton)

        recyclerView.layoutManager = LinearLayoutManager(context)

        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = userAddresses[position]
                val text1 = holder.itemView.findViewById<TextView>(android.R.id.text1)
                val text2 = holder.itemView.findViewById<TextView>(android.R.id.text2)

                text1.text = item.name // e.g. "Home"
                text2.text = item.toFormattedString() // e.g. "123 Main St..."

                holder.itemView.setOnClickListener {
                    selectedDeliveryAddress = item.toFormattedString()
                    updateSelectedAddressUI(item.name)
                    dialog.dismiss()
                }
            }

            override fun getItemCount() = userAddresses.size
        }

        addButton.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), AddressFormActivity::class.java)
            addressResultLauncher.launch(intent)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun updateSelectedAddressUI(label: String? = null) {
        if (selectedDeliveryAddress != null) {
            binding.selectedAddressLabel.text = label ?: "Delivery Location"
            binding.selectedAddressDetail.text = selectedDeliveryAddress
        } else {
            binding.selectedAddressLabel.text = "No Address Selected"
            binding.selectedAddressDetail.text = "Tap to add..."
        }
    }

    private fun fetchUserAddresses() {
        val uid = auth.currentUser?.uid ?: return

        database.child("Users").child(uid).child("Addresses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userAddresses.clear()
                for (child in snapshot.children) {
                    val item = child.getValue(AddressItem::class.java)
                    if (item != null) {
                        userAddresses.add(item)
                    }
                }

                // Auto-Select Logic
                if (_binding != null) {
                    if (userAddresses.isNotEmpty() && selectedDeliveryAddress == null) {
                        // Default to first
                        val default = userAddresses[0]
                        selectedDeliveryAddress = default.toFormattedString()
                        updateSelectedAddressUI(default.name)
                    }

                    // Validation: Ensure selected address is still valid in list logic (optional but safer)
                    if (selectedDeliveryAddress != null) {
                        val exists = userAddresses.any { it.toFormattedString() == selectedDeliveryAddress }
                        if (!exists) {
                            if (userAddresses.isNotEmpty()) {
                                val fallback = userAddresses[0]
                                selectedDeliveryAddress = fallback.toFormattedString()
                                updateSelectedAddressUI(fallback.name)
                            } else {
                                selectedDeliveryAddress = null
                                updateSelectedAddressUI()
                            }
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun saveNewAddress(item: AddressItem) {
        val uid = auth.currentUser?.uid ?: return
        database.child("Users").child(uid).child("Addresses").push().setValue(item)
            .addOnSuccessListener {
                Toast.makeText(context, "Address added!", Toast.LENGTH_SHORT).show()
                selectedDeliveryAddress = item.toFormattedString()
                if (_binding != null) updateSelectedAddressUI(item.name)
                fetchUserAddresses()
            }
    }

    // ... (Keep all robust Transaction/Order logic below exactly as it was) ...
    // The only diff is 'selectedDeliveryAddress' is now populated via toFormattedString()

    private fun recalculateTotals() {
        if (_binding == null) return
        val isDelivery = binding.radioDelivery.isChecked
        val subtotal = CartManager.getCartTotal()
        if (isDelivery) {
            binding.deliveryAddressSection.visibility = View.VISIBLE
            binding.deliveryFeeLayout.visibility = View.VISIBLE
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
            binding.deliveryAddressSection.visibility = View.GONE
            binding.deliveryFeeLayout.visibility = View.GONE
            calculatedDeliveryFee = 0.0
        }
        val finalTotal = subtotal + calculatedDeliveryFee
        binding.subtotalTextView.text = String.format(Locale.US, "$%.2f", subtotal)
        binding.totalPriceTextView.text = String.format(Locale.US, "$%.2f", finalTotal)
    }

    private fun setupRecyclerView() {
        cartAdapter = CartAdapter(CartManager.getCartItems(),
            { cartItem -> cartItem.product?.productId?.let { CartManager.updateQuantity(it, cartItem.quantityInCart) }; updateCartView() },
            { cartItem -> cartItem.product?.productId?.let { CartManager.removeItem(it) }; updateCartView() }
        )
        binding.cartRecyclerView.apply { layoutManager = LinearLayoutManager(context); adapter = cartAdapter }
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
        reserveNextItem(itemsToBuy, 0, mutableListOf())
    }

    private fun reserveNextItem(items: List<com.guc_proj.signaling_proj.CartItem>, index: Int, reservedItems: MutableList<com.guc_proj.signaling_proj.CartItem>) {
        if (index >= items.size) { finalizeOrder(); return }
        val currentItem = items[index]
        val productId = currentItem.product?.productId ?: return
        val quantityNeeded = currentItem.quantityInCart
        val productRef = FirebaseDatabase.getInstance().getReference("Products").child(productId).child("quantity")
        productRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val stock = currentData.getValue(Int::class.java) ?: return Transaction.success(currentData)
                if (stock < quantityNeeded) return Transaction.abort()
                currentData.value = stock - quantityNeeded
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    reservedItems.add(currentItem)
                    reserveNextItem(items, index + 1, reservedItems)
                } else {
                    if (_binding != null && context != null) {
                        val failedProductName = currentItem.product?.name ?: "Item"
                        AlertDialog.Builder(requireContext())
                            .setTitle("Order Failed")
                            .setMessage("Unfortunately, '$failedProductName' is out of stock.")
                            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                            .show()
                        rollbackStock(reservedItems)
                    }
                }
            }
        })
    }

    private fun rollbackStock(itemsToRestore: List<com.guc_proj.signaling_proj.CartItem>) {
        val productsRef = FirebaseDatabase.getInstance().getReference("Products")
        itemsToRestore.forEach { item ->
            val productId = item.product?.productId ?: return@forEach
            val quantityToRestore = item.quantityInCart
            productsRef.child(productId).child("quantity").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentQty = currentData.getValue(Int::class.java) ?: return Transaction.success(currentData)
                    currentData.value = currentQty + quantityToRestore
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
        }
        if (_binding != null) binding.placeOrderButton.isEnabled = true
    }

    private fun finalizeOrder() {
        val buyerId = auth.currentUser?.uid ?: return
        val itemsMap = CartManager.getCartItemsMap()
        val sellerId = CartManager.getSellerId()
        val finalTotal = CartManager.getCartTotal() + calculatedDeliveryFee
        if (sellerId == null) return
        database.child("Users").child(buyerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(buyerSnap: DataSnapshot) {
                val buyerName = buyerSnap.getValue(User::class.java)?.name ?: "Unknown"
                database.child("Users").child(sellerId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(sellerSnap: DataSnapshot) {
                        val sellerName = sellerSnap.getValue(User::class.java)?.name ?: "Unknown"
                        val orderId = database.child("Orders").push().key ?: UUID.randomUUID().toString()
                        val order = Order(
                            orderId = orderId, buyerId = buyerId, sellerId = sellerId,
                            buyerName = buyerName, sellerName = sellerName, items = itemsMap,
                            totalPrice = finalTotal, status = Order.STATUS_PENDING,
                            deliveryType = if (binding.radioDelivery.isChecked) Order.TYPE_DELIVERY else Order.TYPE_PICKUP,
                            deliveryAddress = if (binding.radioDelivery.isChecked) selectedDeliveryAddress else null,
                            deliveryFee = calculatedDeliveryFee
                        )
                        saveOrderToFirebase(orderId, order)
                    }
                    override fun onCancelled(e: DatabaseError) { if (_binding != null) binding.placeOrderButton.isEnabled = true }
                })
            }
            override fun onCancelled(e: DatabaseError) { if (_binding != null) binding.placeOrderButton.isEnabled = true }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}