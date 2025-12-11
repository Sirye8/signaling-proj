# Android Food Ordering & VoIP Signaling App

A feature-rich native Android application that combines a multi-role food ordering platform with a custom-built, real-time VoIP communication system. This project demonstrates advanced Android capabilities including foreground services, raw UDP networking, and cloud integration.

## Project Overview

This system serves three distinct user roles within a single application ecosystem:
1.  **Buyers**: Browse shops, manage carts, and place food orders.
2.  **Sellers**: Manage product inventory and process incoming orders.
3.  **Delivery Partners**: View available jobs, pick up orders, and mark them as delivered.

Beyond standard e-commerce features, the app includes a **custom VoIP signaling engine** built from scratch using UDP sockets, allowing users (Buyers, Sellers, and Delivery agents) to discover each other on a local network and establish voice calls without third-party WebRTC libraries.

---

## Key Features

### Buyer Features
* **Shop Discovery**: Browse a list of available shops and view product catalogs.
* **Smart Cart**: Add/remove items and calculate totals dynamically.
* **Order Tracking**: Real-time status updates (Prepared, Out for Delivery, Delivered).
* **Profile Management**: Update personal details and upload profile images (stored on AWS S3).

### Seller Features
* **Inventory Management**: Add, edit, and delete products with image support.
* **Order Dashboard**: Receive real-time order notifications.
* **Workflow Control**: Accept/Reject orders and update status (e.g., "Ready for Pickup").

### Delivery Features
* **Job Market**: View a list of orders ready for pickup ("Available Jobs").
* **Delivery Workflow**: Claim jobs, pick up orders from sellers, and confirm delivery to buyers.
* **History**: View past delivery performance.

### VoIP & Networking (Signaling Project)
* **Peer Discovery**: Auto-discovery of other users on the local network via UDP Broadcast (Port 5001).
* **Custom Signaling Protocol**: Implements a custom handshake protocol (`SIG:RING`, `SIG:ACC`, `SIG:REJ`, `SIG:END`) over raw sockets.
* **Real-Time Audio**: Full-duplex audio streaming using `AudioRecord` and `AudioTrack` via UDP packets.
* **Foreground Service**: Persistent service maintains network listening and handles calls even when the app is minimized.
* **Network Analysis**: Built for packet sniffing and traffic analysis of VoIP sessions.

---

## Tech Stack

* **Language**: Kotlin
* **Minimum SDK**: 24 (Android 7.0)
* **Target SDK**: 36 (Android 14/15)
* **Architecture**: MVVM / Service-based Architecture
* **Concurrency**: Kotlin Coroutines & Dispatchers

### Backend & Cloud Services
* **Authentication**: Firebase Auth (Email/Password)
* **Database**: Firebase Realtime Database (JSON tree for Users, Orders, Products)
* **Storage**: Amazon Web Services (AWS) S3 (Image hosting)

### Core Libraries
* **Networking**: `java.net.DatagramSocket`, `java.net.InetAddress`
* **Media**: `android.media.AudioRecord`, `android.media.AudioTrack`, `AcousticEchoCanceler`
* **Image Loading**: Glide
* **UI Components**: Material Design, ViewBinding, Navigation Component

---

## Setup & Installation

### Prerequisites
1.  Android Studio (Latest Version)
2.  Firebase Project (with Auth and Realtime Database enabled)
3.  AWS S3 Bucket (for image storage)

### Configuration Steps

1.  **Clone the Repository**
    ```bash
    git clone [https://github.com/sirye8/signaling-proj.git](https://github.com/sirye8/signaling-proj.git)
    ```

2.  **Firebase Setup**
    * Add your `google-services.json` file to the `app/` directory.

3.  **Local Properties (Secrets)**
    Create a `local.properties` file in the root directory to secure your API keys. Do not hardcode them. Add the following lines:
    ```properties
    sdk.dir=/path/to/your/android/sdk
    AWS_ACCESS_KEY=your_access_key_here
    AWS_SECRET_KEY=your_secret_key_here
    S3_BUCKET_NAME=your_bucket_name_here
    ```

4.  **Permissions**
    The app requires the following permissions (handled dynamically at runtime):
    * `RECORD_AUDIO`: For VoIP calls.
    * `read_media_images` / `read_external_storage`: For uploading product/profile pictures.
    * `POST_NOTIFICATIONS`: For order updates and incoming calls.

---

## VoIP Implementation Details

The VoIP feature is powered by `AppService.kt`, a bound Foreground Service that manages the lifecycle of network connections.

* **Discovery**: The app broadcasts `DISCOVER_VOIP:[PORT]|[NAME]|[ROLE]` packets to `255.255.255.255` on port **5001** every few seconds to build a dynamic list of active peers.
* **Signaling**:
    * **Call Request**: Sends `SIG:RING` to the target IP on port **5000** (default).
    * **Accept**: Receiver sends `SIG:ACC` to start the audio stream.
    * **Audio Stream**: 16-bit PCM Mono audio at 16000Hz sample rate is captured, packetized, and sent via UDP.

---

## Screen Roles & Flow

| Role | Entry Point | Key Activities |
| :--- | :--- | :--- |
| **Buyer** | `BuyerHomeActivity` | Browse -> Add to Cart -> Checkout -> Track |
| **Seller** | `SellerHomeActivity` | Add Product -> Wait for Order -> Accept -> Prepare |
| **Delivery** | `DeliveryHomeActivity` | Refresh Jobs -> Pickup -> Deliver |

## License

Distributed under the MIT License. See `LICENSE` for more information.