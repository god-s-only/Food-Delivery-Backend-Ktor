# 🍔 **Food Delivery App Backend Ktor**

Welcome to the **Food Delivery App Backend**! This project is a robust backend built with **Ktor** and designed to power a food delivery ecosystem comprising three main applications:

1. **Customer App**: For users to explore restaurants, view menu items, place orders, and track deliveries.
2. **Rider App**: For delivery personnel to manage and update order statuses and share live locations.
3. **Restaurant App**: For restaurant owners to manage their listings, update order statuses, and oversee operations.

This backend is scalable, modular, and optimized for real-world deployment scenarios.

---

## 🏗️ **Project Structure**

The backend follows a clean and modular structure:

- `configs/`
  - Contains configuration files.
- `database/`
  - Includes database models and table definitions.
- `models/`
  - Houses data classes used for JSON serialization and business logic.
- `routes/`
  - Defines routes for handling API endpoints for various features.
- `services/`
  - Contains the core business logic and operations.
- `utils/`
  - Provides utility functions like error handling and helper methods.
- `Application.kt`
  - The entry point for the Ktor application.

## 🛠️ **Key Features**

### **1. Authentication**
- **Email and Password Login**: Secure authentication using hashed passwords.
- **OAuth Support**: Google and Facebook login integration.
- **JWT-based Authentication**: Stateless and secure token-based authorization.

### **2. Categories**
- Add and manage categories for restaurant menus (e.g., Fast Food, Desserts, Beverages).
- Preloaded with commonly used categories.

### **3. Restaurants**
- Add restaurants with the following details:
  - **Name**
  - **Address**
  - **Geolocation** (latitude, longitude)
  - **Associated category**
- Fetch restaurants based on proximity to the user (within a 5km radius).

### **4. Menu Items**
- Manage menu items for restaurants:
  - **Name**
  - **Description**
  - **Price**
  - **Optional AR metadata** for immersive product viewing.

### **5. Orders**
- Handle order placements:
  - **Customer details**
  - **Restaurant details**
  - **List of menu items**
- Manage order statuses:
  - Pending → Preparing → Ready → Picked Up → Delivered.

### **6. Live Location Tracking**
- **Rider Location Sharing**: Real-time tracking using websockets for live updates.
- **Customer View**: Track delivery progress on a map.

### **7. Reviews**
- Customers can leave ratings and reviews for orders.
## 🛠️ **Tech Stack**

### **Backend**
- **Ktor**: Fast and lightweight Kotlin framework for backend development.
- **Exposed**: ORM library for database operations.
- **Kotlin**: For clean, concise, and expressive code.

### **Database**
- **MySQL**: Reliable and scalable relational database.
- **Spatial Data Support**: Geolocation-based queries for restaurants.

### **Authentication**
- **JWT**: For secure and stateless session management.
- **OAuth**: Google and Facebook login support.

---

## 🚀 **Getting Started**

### **Prerequisites**
- **Kotlin 1.8+**
- **MySQL**
- **Gradle**

### **Setup**

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/food-delivery-backend.git
   cd food-delivery-backend
2. **Start the Ktor server:**:
   ```bash
   ./gradlew run  
3. **The server will be running at http://localhost:8080.**


## 📚 **Endpoints Overview**

### **Authentication**
| Method | Endpoint         | Description                       |
|--------|------------------|-----------------------------------|
| POST   | `/auth/register` | Register a new user              |
| POST   | `/auth/login`    | Login with email and password    |
| POST   | `/auth/oauth`    | Login with Google or Facebook    |

### **Categories**
| Method | Endpoint          | Description                     |
|--------|-------------------|---------------------------------|
| GET    | `/categories`     | Fetch all categories           |
| POST   | `/categories`     | Add a new category (Admin)     |

### **Restaurants**
| Method | Endpoint               | Description                                       |
|--------|------------------------|---------------------------------------------------|
| GET    | `/restaurants`         | Fetch all restaurants near a location            |
| GET    | `/restaurants/{id}`    | Fetch details of a specific restaurant           |
| POST   | `/restaurants`         | Add a new restaurant (Owner/Admin)               |

### **Orders**
| Method | Endpoint               | Description                                  |
|--------|------------------------|----------------------------------------------|
| POST   | `/orders`              | Place a new order                           |
| GET    | `/orders/{id}`         | Fetch details of a specific order           |
| PATCH  | `/orders/{id}/status`  | Update the status of an order               |
