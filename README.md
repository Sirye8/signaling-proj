# Android Food Ordering Application

This repository contains a comprehensive food ordering platform built for Android. Inspired by services like Talabat, the project features two distinct applications—one for buyers and one for sellers—developed with a modern tech stack to handle real-time data, cloud storage, and network communication.

## Project Overview

The system operates on a dual-sided model with two separate, standalone Android applications:

* **Buyer App (Ordering Hub)**: Allows users to browse shops, manage a shopping cart, place orders, and manage their profile.

* **Seller App (Provider Console)**: Enables shop owners to manage their inventory, receive and handle orders in real-time, and update their shop details.

## Core Features

### Buyer Functionality

* **User Authentication**: Secure sign-up and login for buyers.
* **Shop & Product Browsing**: View a list of available shops and their products in a grid layout.
* **Cart Management**: Add/remove items, adjust quantities, and view the total price.
* **Order Placement**: Finalize and send orders directly to the seller's application.
* **Profile Management**: Edit personal information and upload a profile picture.

### Seller Functionality

* **User Authentication**: Secure sign-up and login for sellers.
* **Inventory Control**: Add, edit, or remove grocery items from their catalog.
* **Real-Time Order Reception**: Receive and view incoming orders instantly.
* **Order Management**: Accept or reject orders based on stock availability.
* **Profile Management**: Update shop details and personal credentials.

### Advanced Features

* **VoIP Calling**: In-app Voice over IP calls between users using SIP and RTP protocols.
* **Network Analysis**: Packet sniffing and analysis of network traffic for VoIP sessions.

## Tech Stack & Architecture

* **Platform**: Native Android
* **Language**: Kotlin
* **Authentication**: Firebase Authentication (Email/Password)
* **Database**: Firebase Realtime Database (for user data, products, and orders)
* **Cloud Storage**: Amazon Web Services (AWS) S3 (for profile and product images)
* **Image Loading**: Glide
* **Networking**: VoIP implementation for real-time communication.