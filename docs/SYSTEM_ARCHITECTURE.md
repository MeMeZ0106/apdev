# SanZin K-Store — System Architecture

## Overview

SanZin K-Store is an Android POS + e-commerce app for a Korean goods store.
It supports two roles: **Seller/Admin** and **Customer**, with full Firebase backend integration.

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        ANDROID CLIENT (App)                          │
│                                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────────────┐   │
│  │  Auth Layer  │  │  UI Layer    │  │       Data Layer          │   │
│  │             │  │              │  │                           │   │
│  │ LoginActiv. │  │ MainActivity │  │  FirebaseFirestore        │   │
│  │ GoogleSignIn│  │ Inventory    │  │  FirebaseAuth             │   │
│  │ OTP Reset   │  │ Profile      │  │  CloudinaryHelper         │   │
│  └─────────────┘  │ Chat         │  │  PayMongoService (Retrofit)│   │
│                   │ Logs         │  │  SharedPreferences (cart) │   │
│                   └──────────────┘  └───────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
          │                    │                    │
          ▼                    ▼                    ▼
  ┌──────────────┐   ┌──────────────────┐  ┌──────────────────┐
  │ Firebase Auth │   │ Cloud Firestore   │  │   Cloudinary CDN │
  │              │   │                  │  │                  │
  │ Email/Pass   │   │ /users           │  │ Product images   │
  │ Google OAuth │   │ /products        │  │ (secure_url)     │
  │ OTP via email│   │ /orders          │  └──────────────────┘
  └──────────────┘   │ /conversations   │
                     │ /passwordResets  │
                     └──────────────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │  Firebase Cloud      │
                   │  Messaging (FCM)     │
                   │                      │
                   │  Push notifications  │
                   │  for new messages    │
                   └──────────────────────┘
                              │ (payment)
                              ▼
                   ┌──────────────────────┐
                   │     PayMongo API     │
                   │                      │
                   │  GCash / Maya        │
                   │  Payment sources     │
                   └──────────────────────┘
```

---

## Component Breakdown

### 1. Activities & Fragments

| Component | Role | User |
|---|---|---|
| `LoginActivity` | Auth: email/password, Google Sign-In, OTP forgot password | Both |
| `MainActivity` | Hosts ViewPager + Seller POS panel OR Customer home | Both |
| `ProductListFragment` | Product grid with category chips; live Firestore listener | Both |
| `CartFragment` | Shopping cart review | Customer |
| `CheckoutFragment` | Order summary + payment method select | Customer |
| `InventoryActivity` | Product management grid (CRUD via AdminProductActivity) | Seller |
| `AdminProductActivity` | Add / Edit / Delete a product, image upload | Seller |
| `ProfileActivity` | Display profile, role badge, change password | Both |
| `LogsActivity` | Order history and print/export CSV | Seller |
| `ChatActivity` | Real-time 1-to-1 chat (customer ↔ seller) | Both |
| `SellerInboxActivity` | Inbox: list of all customer conversations | Seller |
| `SettingsActivity` | Dark/Light mode toggle | Both |

### 2. Adapters

| Adapter | Used In |
|---|---|
| `ProductAdapter` | `ProductListFragment`, `InventoryActivity` |
| `CartAdapter` | `CartFragment`, edit-cart dialog |
| `ConversationAdapter` | `SellerInboxActivity` |
| `MessageAdapter` | `ChatActivity` |

### 3. Models

| Model | Firestore Collection | Key Fields |
|---|---|---|
| `Product` | `/products` | name, price, imgUrl, category, subCategory, available |
| `Order` | `/orders` | userId, items[], totalAmount, paymentMethod, isPaid, status |
| `CartItem` | (in-memory / SharedPreferences) | product, quantity |
| `Conversation` | `/conversations` | participantIds[], lastMessage, unreadCount |
| `Message` | `/conversations/{id}/messages` | senderId, text, timestamp |

### 4. External Services

| Service | Purpose | SDK/Library |
|---|---|---|
| Firebase Auth | Authentication (Email + Google) | `firebase-auth` |
| Cloud Firestore | NoSQL database | `firebase-firestore` |
| Firebase Cloud Messaging | Push notifications | `firebase-messaging` |
| Cloudinary | Product image CDN & upload | `cloudinary-android` |
| Picasso | Image loading & caching | `com.squareup.picasso` |
| PayMongo | Online payments (GCash, Maya) | Retrofit + REST |
| Imgur | Secondary image upload fallback | OkHttp + REST |

---

## Firestore Data Structure

```
(default) [database]
├── users/
│   └── {uid}/
│       ├── displayName: "John Doe"
│       ├── email: "john@example.com"
│       ├── isSeller: false
│       ├── provider: "email" | "google"
│       ├── fcmToken: "..."
│       └── createdAt: Timestamp
│
├── products/
│   └── {productId}/
│       ├── name: "Shin Ramyun"
│       ├── description: "..."
│       ├── price: 65.0
│       ├── imgUrl: "https://res.cloudinary.com/..."
│       ├── category: "Goods" | "Meals" | "Beverages"
│       ├── subCategory: "Ramen" | "Milktea" | ...
│       ├── available: true | false
│       └── createdAt: Timestamp
│
├── orders/
│   └── {orderId}/
│       ├── userId: "..."
│       ├── items: [ { product: {...}, quantity: 2 }, ... ]
│       ├── totalAmount: 130.0
│       ├── paymentMethod: "CASH_POS" | "PAY_ON_PICKUP" | "GCASH" | "MAYA"
│       ├── isPaid: true | false
│       ├── status: "PENDING" | "COMPLETED"
│       └── timestamp: long
│
├── conversations/
│   └── {conversationId}/
│       ├── participantIds: ["uid1", "uid2"]
│       ├── lastMessage: "Hello"
│       ├── lastMessageTime: Timestamp
│       └── messages/ [subcollection]
│           └── {msgId}/
│               ├── senderId: "..."
│               ├── text: "..."
│               └── timestamp: Timestamp
│
└── passwordResets/
    └── {email}/
        ├── otp: "123456"
        ├── expiresAt: long (millis)
        └── email: "..."
```

---

## Role-Based Access

```
App Launch
    │
    ├──[isSeller = true]──► Seller POS View
    │                        • Product grid (all products)
    │                        • Cart + Accept/Cancel
    │                        • Inventory management
    │                        • Order logs + print/CSV
    │                        • Seller inbox (chat)
    │
    ├──[isSeller = false]──► Customer View
    │                        • Product grid (available only shown as orderable)
    │                        • Cart + Checkout
    │                        • Payment (Cash/GCash/Maya)
    │                        • Chat with seller
    │
    └──[Guest]──────────────► Browse Only
                              • View products
                              • Blocked from cart/checkout
```

