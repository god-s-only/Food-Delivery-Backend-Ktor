package com.codewithfk.database

import com.codewithfk.database.migrations.updateOwnerPassword
import com.codewithfk.model.Category
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object DatabaseFactory {
    fun init() {
        val driverClassName = "com.mysql.cj.jdbc.Driver"
        val jdbcURL = "jdbc:mysql://localhost:3306/school_keke"
        val user = "root"
        val password = "root"

        try {
            Class.forName(driverClassName)
            Database.connect(jdbcURL, driverClassName, user, password)

            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    UsersTable,
                    CategoriesTable,
                    SchoolsTable,
                    KekeVehiclesTable,
                    AddressesTable,
                    OrdersTable,
                    OrderItemsTable,
                    CartTable,
                    NotificationsTable,
                    RiderLocationsTable,
                    DeliveryRequestsTable,
                    RiderRejectionsTable
                )

                // Check if rider_id column exists
                val riderIdExists = exec(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'orders'
                    AND COLUMN_NAME = 'rider_id'
                    """
                ) { it.next(); it.getInt(1) } ?: 0 > 0

                if (!riderIdExists) {
                    exec("ALTER TABLE orders ADD COLUMN rider_id VARCHAR(36) NULL;")
                    exec("""
                        ALTER TABLE orders
                        ADD CONSTRAINT fk_orders_rider
                        FOREIGN KEY (rider_id) REFERENCES users(id);
                    """)
                }
            }
        } catch (e: Exception) {
            println("Database initialization failed: ${e.message}")
            throw e
        }
    }
}

fun Application.migrateDatabase() {
    transaction {
        try {
            // Migration 1: Add FCM token to users
            val fcmTokenExists = exec(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'users' AND COLUMN_NAME = 'fcm_token'
                """
            ) { it.next(); it.getInt(1) } ?: 0 > 0

            if (!fcmTokenExists) {
                exec("ALTER TABLE users ADD COLUMN fcm_token VARCHAR(255) NULL")
                println("Added fcm_token column to users table")
            }

            // Migration 2: Add category to keke_vehicles
            val categoryExists = exec(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'keke_vehicles' AND COLUMN_NAME = 'category'
                """
            ) { it.next(); it.getInt(1) } ?: 0 > 0

            if (!categoryExists) {
                exec("ALTER TABLE keke_vehicles ADD COLUMN category VARCHAR(100) NULL")
                println("Added category column to keke_vehicles table")
            }

            // Migration 3: Add is_available to keke_vehicles
            val isAvailableExists = exec(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'keke_vehicles' AND COLUMN_NAME = 'is_available'
                """
            ) { it.next(); it.getInt(1) } ?: 0 > 0

            if (!isAvailableExists) {
                exec("ALTER TABLE keke_vehicles ADD COLUMN is_available BOOLEAN DEFAULT TRUE")
                println("Added is_available column to keke_vehicles table")
            }

            // Migration 4: Update all schools to be owned by owner1@example.com
            val owner1Id = UsersTable
                .select { UsersTable.email eq "owner1@example.com" }
                .map { it[UsersTable.id] }
                .firstOrNull()

            if (owner1Id != null) {
                SchoolsTable.update { it[ownerId] = owner1Id }
                println("Updated all schools to be owned by owner1@example.com")
            } else {
                println("Warning: owner1@example.com not found, skipping school ownership migration")
            }

            updateOwnerPassword()
            println("All migrations completed successfully")
        } catch (e: Exception) {
            println("Migration failed: ${e.message}")
            throw e
        }
    }
}

fun Application.seedDatabase() {
    environment.monitor.subscribe(ApplicationStarted) {
        transaction {
            val owner1Id = UUID.randomUUID()
            val owner2Id = UUID.randomUUID()
            val riderId = UUID.randomUUID()

            if (UsersTable.selectAll().empty()) {
                println("Seeding users...")
                UsersTable.insert {
                    it[id] = owner1Id
                    it[email] = "owner1@example.com"
                    it[name] = "School Owner"
                    it[role] = "OWNER"
                    it[authProvider] = "email"
                    it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
                UsersTable.insert {
                    it[id] = owner2Id
                    it[email] = "owner2@example.com"
                    it[name] = "Another Owner"
                    it[role] = "OWNER"
                    it[authProvider] = "email"
                    it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
            }

            if (UsersTable.select { UsersTable.role eq "RIDER" }.empty()) {
                UsersTable.insert {
                    it[id] = riderId
                    it[email] = "rider@example.com"
                    it[name] = "Default Rider"
                    it[role] = "RIDER"
                    it[authProvider] = "email"
                    it[passwordHash] = "111111"
                    it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
                println("Seeded default users: owner1@example.com, owner2@example.com, rider@example.com")

                RiderLocationsTable.insert {
                    it[this.riderId] = riderId
                    it[latitude] = 37.7749
                    it[longitude] = -122.4194
                    it[isAvailable] = true
                    it[lastUpdated] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
            }

            // Seed categories
            val categoryIds = if (CategoriesTable.selectAll().empty()) {
                println("Seeding categories...")
                val categories = listOf(
                    Category(id = UUID.randomUUID().toString(), name = "Primary School", imageUrl = "https://example.com/primary.png"),
                    Category(id = UUID.randomUUID().toString(), name = "Secondary School", imageUrl = "https://example.com/secondary.png"),
                    Category(id = UUID.randomUUID().toString(), name = "University", imageUrl = "https://example.com/university.png"),
                    Category(id = UUID.randomUUID().toString(), name = "Polytechnic", imageUrl = "https://example.com/poly.png"),
                    Category(id = UUID.randomUUID().toString(), name = "Vocational", imageUrl = "https://example.com/vocational.png"),
                    Category(id = UUID.randomUUID().toString(), name = "College", imageUrl = "https://example.com/college.png"),
                    Category(id = UUID.randomUUID().toString(), name = "Tutorial Center", imageUrl = "https://example.com/tutorial.png")
                )
                categories.associate { category ->
                    val uuid = UUID.fromString(category.id)
                    CategoriesTable.insert {
                        it[id] = uuid
                        it[name] = category.name
                        it[imageUrl] = category.imageUrl
                        it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                    }
                    category.name to uuid
                }
            } else {
                CategoriesTable.selectAll().associate { it[CategoriesTable.name] to it[CategoriesTable.id] }
            }

            // Seed schools
            if (SchoolsTable.selectAll().empty()) {
                println("Seeding schools...")
                val schools = listOf(
                    Triple(Pair("Sunrise Academy", "https://example.com/sunrise.png"), "12 School Rd, Lagos", Triple(6.5244, 3.3792, "Primary School")),
                    Triple(Pair("Greenfield Secondary", "https://example.com/greenfield.png"), "45 Education Ave, Abuja", Triple(9.0765, 7.3986, "Secondary School")),
                    Triple(Pair("City Polytechnic", "https://example.com/citypoly.png"), "78 Poly Close, Ibadan", Triple(7.3775, 3.9470, "Polytechnic")),
                    Triple(Pair("Crown University", "https://example.com/crown.png"), "1 Crown Gate, Enugu", Triple(6.4584, 7.5464, "University")),
                    Triple(Pair("Heritage Vocational", "https://example.com/heritage.png"), "33 Skills Lane, Kano", Triple(12.0022, 8.5920, "Vocational")),
                    Triple(Pair("Bright Future College", "https://example.com/bright.png"), "20 Future Rd, Port Harcourt", Triple(4.8156, 7.0498, "College"))
                )
                SchoolsTable.batchInsert(schools) { school ->
                    this[SchoolsTable.id] = UUID.randomUUID()
                    this[SchoolsTable.ownerId] = owner1Id
                    this[SchoolsTable.name] = school.first.first
                    this[SchoolsTable.address] = school.second
                    this[SchoolsTable.latitude] = school.third.first
                    this[SchoolsTable.longitude] = school.third.second
                    this[SchoolsTable.imageUrl] = school.first.second
                    this[SchoolsTable.categoryId] = categoryIds[school.third.third] ?: error("Category not found: ${school.third.third}")
                    this[SchoolsTable.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                }
                println("Schools seeded: ${schools.map { it.first.first }}")
            }

            // Seed keke vehicles with real images and driver names
            if (KekeVehiclesTable.selectAll().empty()) {
                println("Seeding keke vehicles...")
                val schools = SchoolsTable.selectAll().associate { it[SchoolsTable.name] to it[SchoolsTable.id] }

                // Each entry: (vehicleName, driverName - description, price, imageUrl, schoolName)
                data class KekeVehicleEntry(
                    val name: String,
                    val description: String,
                    val price: Double,
                    val imageUrl: String,
                    val schoolName: String
                )

                val kekeVehicles = listOf(
                    KekeVehicleEntry(
                        name = "Keke Marwa — Emeka Okafor",
                        description = "Driver: Emeka Okafor | Blue Keke Marwa | Plate: LG-234-KE | Seats 3 | Air-cooled engine",
                        price = 500.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg",
                        schoolName = "Sunrise Academy"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Napep — Chukwudi Eze",
                        description = "Driver: Chukwudi Eze | Yellow Keke Napep | Plate: LG-891-KN | Seats 3 | Well maintained",
                        price = 550.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg",
                        schoolName = "Sunrise Academy"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Marwa — Tunde Adeyemi",
                        description = "Driver: Tunde Adeyemi | Red Keke Marwa | Plate: LG-445-KM | Seats 3 | Punctual & reliable",
                        price = 500.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg",
                        schoolName = "Sunrise Academy"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Napep — Musa Ibrahim",
                        description = "Driver: Musa Ibrahim | Green Keke Napep | Plate: AB-112-KN | Seats 3 | Fast & safe",
                        price = 480.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg",
                        schoolName = "Greenfield Secondary"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Marwa — Sule Abubakar",
                        description = "Driver: Sule Abubakar | White Keke Marwa | Plate: AB-378-KM | Seats 3 | Comfortable ride",
                        price = 500.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg",
                        schoolName = "Greenfield Secondary"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Napep — Biodun Fashola",
                        description = "Driver: Biodun Fashola | Orange Keke Napep | Plate: OY-556-KN | Seats 3 | Friendly driver",
                        price = 460.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg",
                        schoolName = "City Polytechnic"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Marwa — Ifeanyi Nwosu",
                        description = "Driver: Ifeanyi Nwosu | Black Keke Marwa | Plate: OY-203-KM | Seats 3 | Early riser, always on time",
                        price = 510.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg",
                        schoolName = "City Polytechnic"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Napep — Gbenga Olatunji",
                        description = "Driver: Gbenga Olatunji | Silver Keke Napep | Plate: EN-741-KN | Seats 3 | Knows every school route",
                        price = 520.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg",
                        schoolName = "Crown University"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Marwa — Yakubu Garba",
                        description = "Driver: Yakubu Garba | Blue Keke Marwa | Plate: KN-009-KM | Seats 3 | Safe driving record",
                        price = 490.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg",
                        schoolName = "Heritage Vocational"
                    ),
                    KekeVehicleEntry(
                        name = "Keke Napep — Festus Okoro",
                        description = "Driver: Festus Okoro | Yellow Keke Napep | Plate: RV-334-KN | Seats 3 | 5-star rated driver",
                        price = 530.0,
                        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg",
                        schoolName = "Bright Future College"
                    )
                )

                KekeVehiclesTable.batchInsert(kekeVehicles) { vehicle ->
                    val schoolId = schools[vehicle.schoolName] ?: error("School not found: ${vehicle.schoolName}")
                    this[KekeVehiclesTable.id] = UUID.randomUUID()
                    this[KekeVehiclesTable.schoolId] = schoolId
                    this[KekeVehiclesTable.name] = vehicle.name
                    this[KekeVehiclesTable.description] = vehicle.description
                    this[KekeVehiclesTable.price] = vehicle.price
                    this[KekeVehiclesTable.imageUrl] = vehicle.imageUrl
                    this[KekeVehiclesTable.arModelUrl] = null
                    this[KekeVehiclesTable.category] = null
                    this[KekeVehiclesTable.isAvailable] = true
                    this[KekeVehiclesTable.createdAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                }
                println("Keke vehicles seeded (${kekeVehicles.size} vehicles with driver names and images).")
            }
        }
    }
}
