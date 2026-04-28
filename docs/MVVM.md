# SanZin K-Store — MVVM Architecture

## Current Architecture (MVC-style)

The app currently uses a **Model-View-Controller (MVC)** pattern where Activities/Fragments
directly talk to Firebase. This works but leads to "God Activities" with too many responsibilities.

```
┌──────────────┐      direct call      ┌────────────────────┐
│    View      │ ─────────────────────► │  Firebase / APIs   │
│  (Activity / │                        │                    │
│   Fragment)  │ ◄───── callback ─────  │  Firestore         │
│              │                        │  Auth              │
│  also holds: │                        │  Cloudinary        │
│  - business  │                        │  PayMongo          │
│    logic     │                        └────────────────────┘
│  - UI state  │
│  - data fetch│
└──────────────┘
```

### Problems with current approach
- Activities are 300–700 lines long
- No separation of concerns
- Business logic is hard to test
- Data is lost on screen rotation
- Duplicate Firestore queries in each screen

---

## Recommended MVVM Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     MVVM LAYER DIAGRAM                          │
│                                                                 │
│  ┌──────────────┐   observes    ┌──────────────────────────┐   │
│  │     VIEW     │ ◄──LiveData── │       VIEW MODEL         │   │
│  │              │               │                          │   │
│  │ Activity /   │ ──── calls ──►│  ProductViewModel        │   │
│  │ Fragment     │               │  CartViewModel           │   │
│  │              │               │  AuthViewModel           │   │
│  │ (only UI +   │               │  ChatViewModel           │   │
│  │  observers)  │               │                          │   │
│  └──────────────┘               │  holds: LiveData<>       │   │
│                                 │  survives rotation       │   │
│                                 └────────────┬─────────────┘   │
│                                              │ calls           │
│                                              ▼                 │
│                                 ┌────────────────────────┐     │
│                                 │      REPOSITORY        │     │
│                                 │                        │     │
│                                 │  ProductRepository     │     │
│                                 │  UserRepository        │     │
│                                 │  OrderRepository       │     │
│                                 │  ChatRepository        │     │
│                                 │                        │     │
│                                 │  (single source of     │     │
│                                 │   truth)               │     │
│                                 └────────────┬───────────┘     │
│                                              │                 │
│                              ┌───────────────┼───────────────┐ │
│                              ▼               ▼               ▼ │
│                    ┌──────────────┐ ┌──────────────┐ ┌──────┐ │
│                    │  Firestore   │ │ Firebase Auth │ │Cloud │ │
│                    │  (Remote DS) │ │  (Remote DS) │ │inary │ │
│                    └──────────────┘ └──────────────┘ └──────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## Proposed Package Structure

```
com.example.sanzinkstore/
│
├── model/                          ← Data classes (unchanged)
│   ├── Product.java
│   ├── Order.java
│   ├── CartItem.java
│   ├── Conversation.java
│   └── Message.java
│
├── repository/                     ← NEW: data access layer
│   ├── ProductRepository.java      abstracts Firestore /products
│   ├── OrderRepository.java        abstracts Firestore /orders
│   ├── UserRepository.java         abstracts Firestore /users + Auth
│   └── ChatRepository.java         abstracts Firestore /conversations
│
├── viewmodel/                      ← NEW: business logic + state
│   ├── ProductViewModel.java       LiveData<List<Product>>
│   ├── CartViewModel.java          LiveData<List<CartItem>>, total
│   ├── AuthViewModel.java          LiveData<FirebaseUser>
│   └── ChatViewModel.java          LiveData<List<Message>>
│
├── ui/                             ← Thin Views (observe only)
│   ├── login/
│   │   └── LoginActivity.java
│   ├── main/
│   │   ├── MainActivity.java
│   │   └── ProductListFragment.java
│   ├── inventory/
│   │   ├── InventoryActivity.java
│   │   └── AdminProductActivity.java
│   ├── profile/
│   │   └── ProfileActivity.java
│   ├── chat/
│   │   ├── ChatActivity.java
│   │   └── SellerInboxActivity.java
│   ├── cart/
│   │   ├── CartFragment.java
│   │   └── CheckoutFragment.java
│   └── logs/
│       └── LogsActivity.java
│
├── adapter/                        ← RecyclerView adapters (unchanged)
├── api/                            ← External API helpers (unchanged)
└── service/                        ← FCM service (unchanged)
```

---

## MVVM Implementation Examples

### ProductRepository.java
```java
public class ProductRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Returns a LiveData that auto-updates when Firestore changes. */
    public LiveData<List<Product>> getProducts() {
        MutableLiveData<List<Product>> liveData = new MutableLiveData<>();
        db.collection("products").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            List<Product> list = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snap) {
                Product p = doc.toObject(Product.class);
                p.setId(doc.getId());
                list.add(p);
            }
            liveData.setValue(list);
        });
        return liveData;
    }

    public Task<Void> setAvailability(String productId, boolean available) {
        return db.collection("products").document(productId)
                 .update("available", available);
    }
}
```

### ProductViewModel.java
```java
public class ProductViewModel extends AndroidViewModel {
    private final ProductRepository repo = new ProductRepository();
    private final LiveData<List<Product>> allProducts = repo.getProducts();

    // Expose filtered list based on selected category/subcategory
    private final MutableLiveData<String> selectedCategory = new MutableLiveData<>("Goods");
    private final MutableLiveData<String> selectedSubCategory = new MutableLiveData<>("All");

    public ProductViewModel(@NonNull Application app) { super(app); }

    public LiveData<List<Product>> getAllProducts() { return allProducts; }
    public void setCategory(String cat) { selectedCategory.setValue(cat); }
    public void setSubCategory(String sub) { selectedSubCategory.setValue(sub); }
}
```

### ProductListFragment.java (thin View)
```java
@Override
public View onCreateView(...) {
    // ...setup...
    ProductViewModel vm = new ViewModelProvider(this).get(ProductViewModel.class);

    vm.getAllProducts().observe(getViewLifecycleOwner(), products -> {
        // Just update the adapter — no business logic here
        adapter.submitList(products);
    });
}
```

### CartViewModel.java
```java
public class CartViewModel extends AndroidViewModel {
    private final MutableLiveData<List<CartItem>> cartItems = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<CartItem>> getCartItems() { return cartItems; }

    public void addToCart(Product product) {
        List<CartItem> current = cartItems.getValue();
        // find existing or add new — pure business logic, testable
        cartItems.setValue(current);
    }

    public LiveData<Double> getTotal() {
        return Transformations.map(cartItems, items ->
            items.stream().mapToDouble(CartItem::getTotalPrice).sum());
    }
}
```

---

## Responsibilities Comparison

| Responsibility | Current (MVC) | Recommended (MVVM) |
|---|---|---|
| Firestore query | In Activity/Fragment | Repository |
| Filter products by category | In Activity/Fragment | ViewModel |
| Cart state | In MainActivity field | CartViewModel + LiveData |
| Cart survives rotation? | ❌ No | ✅ Yes (ViewModel lifecycle) |
| Business logic testable? | ❌ Needs UI | ✅ Pure unit tests |
| Auth state | Checked in every Activity | AuthViewModel (shared) |
| Image upload logic | In AdminProductActivity | ProductRepository |

---

## Data Flow Summary

```
User taps "Add to Cart"
        │
        ▼
 ProductListFragment          ← VIEW
   calls vm.addToCart(p)
        │
        ▼
   CartViewModel              ← VIEWMODEL
   updates cartItems LiveData
   recalculates total
        │ (observed)
        ▼
 ProductListFragment          ← VIEW
   updates FAB badge text
   (no logic, just display)
```

```
App opens / screen rotates
        │
        ▼
 ProductListFragment          ← VIEW
   ViewModelProvider.get()
   → returns SAME ViewModel
        │
        ▼
   ProductViewModel           ← VIEWMODEL (survives rotation)
   already has LiveData value
        │
        ▼
 Fragment re-observes         ← VIEW
   gets current product list
   immediately (no reload)
```

