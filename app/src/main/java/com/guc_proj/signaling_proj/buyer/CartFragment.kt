package com.guc_proj.signaling_proj.buyer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.*
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.User
import com.guc_proj.signaling_proj.databinding.FragmentCartBinding
import java.util.* // <-- Make sure this is imported
import java.util.Locale // <-- Add this import

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var cartAdapter: CartAdapter
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

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
        updateCartView()

        binding.placeOrderButton.setOnClickListener {
            placeOrder()
        }

        binding.clearCartButton.setOnClickListener {
            CartManager.clearCart()
            updateCartView()
        }
    }

    private fun setupRecyclerView() {
        cartAdapter = CartAdapter(
            CartManager.getCartItems(),
            { cartItem ->
                cartItem.product?.productId?.let { productId ->
                    CartManager.updateQuantity(productId, cartItem.quantityInCart)
                }
                updateCartView()
            },
            { cartItem ->
                cartItem.product?.productId?.let { productId ->
                    CartManager.removeItem(productId)
                }
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

        if (cartItems.isEmpty()) {
            binding.emptyCartView.visibility = View.VISIBLE
            binding.cartContentGroup.visibility = View.GONE
        } else {
            binding.emptyCartView.visibility = View.GONE
            binding.cartContentGroup.visibility = View.VISIBLE
            val total = CartManager.getCartTotal()
            binding.totalPriceTextView.text = String.format(Locale.US, "Total: $%.2f", total)
        }
    }

    private fun placeOrder() {
        val buyerId = auth.currentUser?.uid
        if (buyerId == null) {
            Toast.makeText(context, "You must be logged in to place an order.", Toast.LENGTH_SHORT).show()
            return
        }

        val items = CartManager.getCartItemsMap()
        val sellerId = CartManager.getSellerId()
        val total = CartManager.getCartTotal()

        if (items.isEmpty() || sellerId == null) {
            Toast.makeText(context, "Your cart is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.placeOrderButton.isEnabled = false
        Toast.makeText(context, "Placing order...", Toast.LENGTH_SHORT).show()

        database.child("Users").child(buyerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(buyerSnapshot: DataSnapshot) {
                val buyerName = buyerSnapshot.getValue<User>()?.name ?: "Unknown Buyer"

                database.child("Users").child(sellerId).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(sellerSnapshot: DataSnapshot) {
                        val sellerName = sellerSnapshot.getValue<User>()?.name ?: "Unknown Seller"

                        val orderId = database.child("Orders").push().key ?: UUID.randomUUID().toString()
                        val order = Order(
                            orderId = orderId,
                            buyerId = buyerId,
                            sellerId = sellerId,
                            buyerName = buyerName,
                            sellerName = sellerName,
                            items = items,
                            totalPrice = total,
                            status = Order.STATUS_PENDING
                        )

                        saveOrderToFirebase(orderId, order)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("CartFragment", "Failed to get seller name: ${error.message}")
                        Toast.makeText(context, "Failed to place order. Could not verify seller.", Toast.LENGTH_SHORT).show()
                        binding.placeOrderButton.isEnabled = true
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CartFragment", "Failed to get buyer name: ${error.message}")
                Toast.makeText(context, "Failed to place order. Could not verify user.", Toast.LENGTH_SHORT).show()
                binding.placeOrderButton.isEnabled = true
            }
        })
    }

    private fun saveOrderToFirebase(orderId: String, order: Order) {
        database.child("Orders").child(orderId).setValue(order)
            .addOnSuccessListener {
                Toast.makeText(context, "Order placed successfully!", Toast.LENGTH_SHORT).show()
                CartManager.clearCart()
                updateCartView()
                binding.placeOrderButton.isEnabled = true
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to place order: ${it.message}", Toast.LENGTH_SHORT).show()
                binding.placeOrderButton.isEnabled = true
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