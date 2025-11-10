package com.guc_proj.signaling_proj.buyer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.guc_proj.signaling_proj.Order
import com.guc_proj.signaling_proj.databinding.FragmentCartBinding
import java.util.*

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var cartAdapter: CartAdapter
    private lateinit var auth: FirebaseAuth

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
            { cartItem -> // On quantity change
                CartManager.updateQuantity(cartItem.product.productId!!, cartItem.quantityInCart)
                updateCartView()
            },
            { cartItem -> // On remove
                CartManager.removeItem(cartItem.product.productId!!)
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
            binding.totalPriceTextView.text = String.format("Total: $%.2f", total)
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

        val database = FirebaseDatabase.getInstance().getReference("Orders")
        val orderId = database.push().key ?: UUID.randomUUID().toString()

        val order = Order(
            orderId = orderId,
            buyerId = buyerId,
            sellerId = sellerId,
            items = items,
            totalPrice = total,
            status = "Pending"
        )

        binding.placeOrderButton.isEnabled = false
        database.child(orderId).setValue(order)
            .addOnSuccessListener {
                Toast.makeText(context, "Order placed successfully!", Toast.LENGTH_SHORT).show()
                CartManager.clearCart()
                updateCartView()
                binding.placeOrderButton.isEnabled = true
                // TODO: Implement stock update logic for Milestone 4
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