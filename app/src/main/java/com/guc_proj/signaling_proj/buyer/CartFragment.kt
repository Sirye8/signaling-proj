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
import com.guc_proj.signaling_proj.CardFormActivity
import com.guc_proj.signaling_proj.CartItem
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.PaymentCard
import com.guc_proj.signaling_proj.R
import com.guc_proj.signaling_proj.User
import com.guc_proj.signaling_proj.databinding.FragmentCartBinding
import java.util.*
import java.util.Locale
import kotlin.math.min

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var cartAdapter: CartAdapter
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

    // --- Address Variables ---
    private val userAddresses = mutableListOf<AddressItem>()
    private var selectedDeliveryAddress: String? = null

    // --- Card Variables ---
    private val userCards = mutableListOf<PaymentCard>()
    private var selectedCard: PaymentCard? = null

    private var availableCredit: Double = 0.0
    private var calculatedDeliveryFee: Double = 0.0

    private val FEE_HIGH = 15.0
    private val FEE_MED = 10.0
    private val FEE_LOW = 5.0
    private val THRESHOLD_SMALL = 30.0
    private val THRESHOLD_MEDIUM = 70.0
    private val THRESHOLD_LARGE = 150.0

    // Address Result
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

    // Card Result
    private val cardResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val number = data.getStringExtra("EXTRA_NUMBER") ?: ""
                val holder = data.getStringExtra("EXTRA_HOLDER") ?: ""
                val expiry = data.getStringExtra("EXTRA_EXPIRY") ?: ""
                val masked = if (number.length >= 4) "**** **** **** ${number.takeLast(4)}" else number

                val newCard = PaymentCard(null, masked, holder, expiry)
                saveNewCard(newCard)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        setupAddressSelector()
        setupCardSelector()

        binding.deliveryRadioGroup.setOnCheckedChangeListener { _, _ -> recalculateTotals() }

        binding.paymentMethodGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.cardSection.visibility = if(checkedId == R.id.radioCard) View.VISIBLE else View.GONE
        }

        binding.useCreditSwitch.setOnCheckedChangeListener { _, _ -> recalculateTotals() }

        binding.addMoreItemsButton.setOnClickListener { navigateToAddMoreItems() }
        binding.placeOrderButton.setOnClickListener { attemptPlaceOrder() }
        binding.clearCartButton.setOnClickListener { CartManager.clearCart(); updateCartView() }

        // Handle Explore Shops click
        binding.exploreShopsButton.setOnClickListener {
            // Navigate to the first tab (Shops) in BuyerHomeActivity
            (activity as? BuyerHomeActivity)?.findViewById<ViewPager2>(R.id.buyer_view_pager)?.currentItem = 0
        }

        updateCartView()
        fetchUserAddresses()
        fetchUserFinancials()
    }

    override fun onResume() {
        super.onResume()
        updateCartView()
        fetchUserAddresses()
        // Re-fetch cards in case edited in PayActivity
        fetchUserFinancials()
    }

    private fun fetchUserFinancials() {
        val uid = auth.currentUser?.uid ?: return

        database.child("Users").child(uid).child("credit").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                availableCredit = snapshot.getValue(Double::class.java) ?: 0.0
                if (_binding != null) {
                    binding.useCreditSwitch.text = String.format(Locale.US, "Use Delivery Credit ($%.2f)", availableCredit)
                    binding.useCreditSwitch.isEnabled = availableCredit > 0
                    recalculateTotals()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        database.child("Users").child(uid).child("Cards").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userCards.clear()
                for (child in snapshot.children) {
                    val card = child.getValue(PaymentCard::class.java)
                    if (card != null) userCards.add(card)
                }
                if (_binding != null) {
                    if (userCards.isNotEmpty()) {
                        if (selectedCard == null || !userCards.any { it.cardId == selectedCard?.cardId }) {
                            selectedCard = userCards[0]
                        }
                        updateSelectedCardUI()
                    } else {
                        selectedCard = null
                        updateSelectedCardUI()
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupCardSelector() {
        binding.cardSelectorCard.setOnClickListener { showCardBottomSheet() }
    }

    private fun updateSelectedCardUI() {
        if (selectedCard != null) {
            binding.selectedCardLabel.text = selectedCard?.cardNumber
            binding.selectedCardDetail.text = selectedCard?.cardHolder
        } else {
            binding.selectedCardLabel.text = "Select Card"
            binding.selectedCardDetail.text = "Tap to choose..."
        }
    }

    private fun showCardBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.layout_bottom_sheet_cards, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.sheetCardsRecycler)
        val addButton = view.findViewById<Button>(R.id.sheetAddCardButton)

        recyclerView.layoutManager = LinearLayoutManager(context)
        // Reuse the updated item_payment_card layout, but hide Edit/Delete buttons for selection mode
        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_payment_card, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = userCards[position]
                holder.itemView.findViewById<TextView>(R.id.cardNumberText).text = item.cardNumber
                holder.itemView.findViewById<TextView>(R.id.cardHolderText).text = item.cardHolder

                // Hide edit/delete for selection list
                holder.itemView.findViewById<View>(R.id.editCardButton).visibility = View.GONE
                holder.itemView.findViewById<View>(R.id.deleteCardButton).visibility = View.GONE

                holder.itemView.setOnClickListener {
                    selectedCard = item
                    updateSelectedCardUI()
                    dialog.dismiss()
                }
            }
            override fun getItemCount() = userCards.size
        }

        addButton.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), CardFormActivity::class.java)
            cardResultLauncher.launch(intent)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun saveNewCard(card: PaymentCard) {
        val uid = auth.currentUser?.uid ?: return
        val key = database.child("Users").child(uid).child("Cards").push().key ?: return
        val cardWithId = card.copy(cardId = key)

        database.child("Users").child(uid).child("Cards").child(key).setValue(cardWithId)
            .addOnSuccessListener {
                Toast.makeText(context, "Card added!", Toast.LENGTH_SHORT).show()
                selectedCard = cardWithId
                if (_binding != null) updateSelectedCardUI()
            }
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
                text1.text = item.name
                text2.text = item.toFormattedString()
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
                    if (item != null) userAddresses.add(item)
                }
                if (_binding != null) {
                    if (userAddresses.isNotEmpty() && selectedDeliveryAddress == null) {
                        val default = userAddresses[0]
                        selectedDeliveryAddress = default.toFormattedString()
                        updateSelectedAddressUI(default.name)
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

    private fun recalculateTotals() {
        if (_binding == null) return
        val subtotal = CartManager.getCartTotal()
        val isDelivery = binding.radioDelivery.isChecked

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

        val totalBeforeDiscount = subtotal + calculatedDeliveryFee
        var discount = 0.0
        if (binding.useCreditSwitch.isChecked) {
            discount = min(totalBeforeDiscount, availableCredit)
            binding.discountLayout.visibility = View.VISIBLE
            binding.discountTextView.text = String.format(Locale.US, "-$%.2f", discount)
        } else {
            binding.discountLayout.visibility = View.GONE
        }

        val finalTotal = totalBeforeDiscount - discount
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
            // Empty State
            binding.emptyCartView.visibility = View.VISIBLE
            binding.exploreShopsButton.visibility = View.VISIBLE // Show Explore Button

            // Hide Cart Content
            binding.cartContentScroll.visibility = View.GONE
            binding.totalsContainer.visibility = View.GONE

            // Hide Bottom Buttons
            binding.placeOrderButton.visibility = View.GONE
            binding.clearCartButton.visibility = View.GONE
        } else {
            // Active State
            binding.emptyCartView.visibility = View.GONE
            binding.exploreShopsButton.visibility = View.GONE // Hide Explore Button

            // Show Cart Content
            binding.cartContentScroll.visibility = View.VISIBLE
            binding.totalsContainer.visibility = View.VISIBLE

            // Show Bottom Buttons
            binding.placeOrderButton.visibility = View.VISIBLE
            binding.clearCartButton.visibility = View.VISIBLE

            recalculateTotals()
        }
    }

    private fun attemptPlaceOrder() {
        if (binding.radioDelivery.isChecked && selectedDeliveryAddress.isNullOrEmpty()) {
            Toast.makeText(context, "Please select a delivery address.", Toast.LENGTH_SHORT).show()
            return
        }
        if (binding.radioCard.isChecked && selectedCard == null) {
            Toast.makeText(context, "Please select a payment card.", Toast.LENGTH_SHORT).show()
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

    private fun reserveNextItem(items: List<CartItem>, index: Int, reservedItems: MutableList<CartItem>) {
        if (index >= items.size) {
            processCreditDeductionAndFinalize()
            return
        }
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
                        Toast.makeText(context, "Item out of stock: ${currentItem.product?.name}", Toast.LENGTH_LONG).show()
                        rollbackStock(reservedItems)
                    }
                }
            }
        })
    }

    private fun rollbackStock(itemsToRestore: List<CartItem>) {
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

    private fun processCreditDeductionAndFinalize() {
        val subtotal = CartManager.getCartTotal()
        val totalBeforeDiscount = subtotal + calculatedDeliveryFee
        val discount = if (binding.useCreditSwitch.isChecked) min(totalBeforeDiscount, availableCredit) else 0.0

        if (discount > 0) {
            val uid = auth.currentUser?.uid ?: return
            database.child("Users").child(uid).child("credit").runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentCredit = currentData.getValue(Double::class.java) ?: 0.0
                    if (currentCredit < discount) return Transaction.abort()
                    currentData.value = currentCredit - discount
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (committed) finalizeOrder(discount)
                    else {
                        if (_binding != null) {
                            Toast.makeText(context, "Credit transaction failed.", Toast.LENGTH_SHORT).show()
                            rollbackStock(CartManager.getCartItems())
                        }
                    }
                }
            })
        } else {
            finalizeOrder(0.0)
        }
    }

    private fun finalizeOrder(discount: Double) {
        val buyerId = auth.currentUser?.uid ?: return
        val itemsMap = CartManager.getCartItemsMap()
        val sellerId = CartManager.getSellerId() ?: return
        val subtotal = CartManager.getCartTotal()
        val totalBeforeDiscount = subtotal + calculatedDeliveryFee
        val finalPrice = totalBeforeDiscount - discount
        val paymentMethod = if (binding.radioCard.isChecked) Order.PAY_CARD else Order.PAY_CASH

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
                            totalPrice = totalBeforeDiscount,
                            status = Order.STATUS_PENDING,
                            deliveryType = if (binding.radioDelivery.isChecked) Order.TYPE_DELIVERY else Order.TYPE_PICKUP,
                            deliveryAddress = if (binding.radioDelivery.isChecked) selectedDeliveryAddress else null,
                            deliveryFee = calculatedDeliveryFee,
                            paymentMethod = paymentMethod,
                            discountApplied = discount,
                            finalPrice = finalPrice
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
                Toast.makeText(context, "Order placed!", Toast.LENGTH_SHORT).show()
                CartManager.clearCart()
                updateCartView()
                binding.placeOrderButton.isEnabled = true
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(context, "Failed to place order.", Toast.LENGTH_SHORT).show()
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
            (activity as? BuyerHomeActivity)?.findViewById<ViewPager2>(R.id.buyer_view_pager)?.currentItem = 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}