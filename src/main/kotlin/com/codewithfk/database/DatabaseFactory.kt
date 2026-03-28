package com.codewithfk.database

import com.codewithfk.database.migrations.updateOwnerPassword
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

                // Add rider_id column if it doesn't exist
                val riderIdExists = exec(
                    """
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'rider_id'
                    """
                ) { it.next(); it.getInt(1) } ?: 0 > 0

                if (!riderIdExists) {
                    exec("ALTER TABLE orders ADD COLUMN rider_id VARCHAR(36) NULL;")
                    exec("""
                        ALTER TABLE orders ADD CONSTRAINT fk_orders_rider
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
            // Add FCM token column if missing
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

            // Add is_available to keke_vehicles if missing
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

            // Seed users
            if (UsersTable.selectAll().empty()) {
                println("Seeding users...")
                UsersTable.insert {
                    it[id] = owner1Id
                    it[email] = "owner1@example.com"
                    it[name] = "Sunrise Academy Admin"
                    it[role] = "OWNER"
                    it[passwordHash] = "111111"
                    it[authProvider] = "email"
                    it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
                UsersTable.insert {
                    it[id] = owner2Id
                    it[email] = "owner2@example.com"
                    it[name] = "Greenfield School Admin"
                    it[role] = "OWNER"
                    it[passwordHash] = "111111"
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
                    it[passwordHash] = "111111"
                    it[authProvider] = "email"
                    it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
                println("Seeded rider: rider@example.com / 111111")

                RiderLocationsTable.insert {
                    it[this.riderId] = riderId
                    it[latitude] = 6.5244
                    it[longitude] = 3.3792
                    it[isAvailable] = true
                    it[lastUpdated] = org.jetbrains.exposed.sql.javatime.CurrentDateTime()
                }
            }

            // Seed schools
            if (SchoolsTable.selectAll().empty()) {
                println("Seeding schools...")

                data class SchoolSeed(val name: String, val address: String, val lat: Double, val lon: Double, val imageUrl: String, val ownerId: UUID)

                val schools = listOf(
                    SchoolSeed("Sunrise Academy", "12 School Rd, Yaba, Lagos", 6.5143, 3.3751, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/SNice.svg/640px-SNice.svg.png", owner1Id),
                    SchoolSeed("Greenfield Secondary School", "45 Education Ave, Wuse, Abuja", 9.0579, 7.4951, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/SNice.svg/640px-SNice.svg.png", owner2Id),
                    SchoolSeed("City Polytechnic Ibadan", "78 Poly Close, Ibadan", 7.3775, 3.9470, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/SNice.svg/640px-SNice.svg.png", owner1Id),
                    SchoolSeed("Crown University Enugu", "1 Crown Gate, GRA, Enugu", 6.4584, 7.5464, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/SNice.svg/640px-SNice.svg.png", owner2Id),
                    SchoolSeed("Heritage Vocational Kano", "33 Skills Lane, Kano", 12.0022, 8.5920, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/SNice.svg/640px-SNice.svg.png", owner1Id),
                    SchoolSeed("Bright Future College Port Harcourt", "20 Future Rd, Port Harcourt", 4.8156, 7.0498, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/SNice.svg/640px-SNice.svg.png", owner2Id)
                )

                val schoolIds = mutableMapOf<String, UUID>()
                schools.forEach { school ->
                    val newId = UUID.randomUUID()
                    schoolIds[school.name] = newId
                    SchoolsTable.insert {
                        it[id] = newId
                        it[ownerId] = school.ownerId
                        it[name] = school.name
                        it[address] = school.address
                        it[latitude] = school.lat
                        it[longitude] = school.lon
                        it[imageUrl] = school.imageUrl
                        it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                    }
                }
                println("Schools seeded: ${schools.map { it.name }}")

                // Seed keke vehicles with real images and driver names
                if (KekeVehiclesTable.selectAll().empty()) {
                    println("Seeding keke vehicles...")

                    data class KekeSeed(
                        val schoolName: String,
                        val name: String,
                        val driverName: String,
                        val description: String,
                        val price: Double,
                        val imageUrl: String
                    )

                    val kekeList = listOf(
                        KekeSeed(
                            schoolName = "Sunrise Academy",
                            name = "Keke Marwa — Emeka Okafor",
                            driverName = "Emeka Okafor",
                            description = "Blue Keke Marwa | Plate: LG-234-KE | Seats 3 | 5 years experience",
                            price = 500.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg"
                        ),
                        KekeSeed(
                            schoolName = "Sunrise Academy",
                            name = "Keke Napep — Chukwudi Eze",
                            driverName = "Chukwudi Eze",
                            description = "Yellow Keke Napep | Plate: LG-891-KN | Seats 3 | Punctual & safe",
                            price = 550.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg"
                        ),
                        KekeSeed(
                            schoolName = "Sunrise Academy",
                            name = "Keke Marwa — Tunde Adeyemi",
                            driverName = "Tunde Adeyemi",
                            description = "Red Keke Marwa | Plate: LG-445-KM | Seats 3 | Reliable morning rider",
                            price = 500.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg"
                        ),
                        KekeSeed(
                            schoolName = "Greenfield Secondary School",
                            name = "Keke Napep — Musa Ibrahim",
                            driverName = "Musa Ibrahim",
                            description = "Green Keke Napep | Plate: AB-112-KN | Seats 3 | Fast & safe",
                            price = 480.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg"
                        ),
                        KekeSeed(
                            schoolName = "Greenfield Secondary School",
                            name = "Keke Marwa — Sule Abubakar",
                            driverName = "Sule Abubakar",
                            description = "White Keke Marwa | Plate: AB-378-KM | Seats 3 | Comfortable & clean",
                            price = 500.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg"
                        ),
                        KekeSeed(
                            schoolName = "City Polytechnic Ibadan",
                            name = "Keke Napep — Biodun Fashola",
                            driverName = "Biodun Fashola",
                            description = "Orange Keke Napep | Plate: OY-556-KN | Seats 3 | Friendly driver",
                            price = 460.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg"
                        ),
                        KekeSeed(
                            schoolName = "City Polytechnic Ibadan",
                            name = "Keke Marwa — Ifeanyi Nwosu",
                            driverName = "Ifeanyi Nwosu",
                            description = "Black Keke Marwa | Plate: OY-203-KM | Seats 3 | Always on time",
                            price = 510.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg"
                        ),
                        KekeSeed(
                            schoolName = "Crown University Enugu",
                            name = "Keke Napep — Gbenga Olatunji",
                            driverName = "Gbenga Olatunji",
                            description = "Silver Keke Napep | Plate: EN-741-KN | Seats 3 | Knows every campus route",
                            price = 520.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg"
                        ),
                        KekeSeed(
                            schoolName = "Heritage Vocational Kano",
                            name = "Keke Marwa — Yakubu Garba",
                            driverName = "Yakubu Garba",
                            description = "Blue Keke Marwa | Plate: KN-009-KM | Seats 3 | 7 years clean record",
                            price = 490.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/Keke_NAPEP_in_Lagos.jpg/640px-Keke_NAPEP_in_Lagos.jpg"
                        ),
                        KekeSeed(
                            schoolName = "Bright Future College Port Harcourt",
                            name = "Keke Napep — Festus Okoro",
                            driverName = "Festus Okoro",
                            description = "Yellow Keke Napep | Plate: RV-334-KN | Seats 3 | 5-star rated driver",
                            price = 530.0,
                            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Keke_napep_abuja.jpg/640px-Keke_napep_abuja.jpg"
                        )
                    )

                    kekeList.forEach { keke ->
                        val sid = schoolIds[keke.schoolName] ?: return@forEach
                        KekeVehiclesTable.insert {
                            it[id] = UUID.randomUUID()
                            it[schoolId] = sid
                            it[name] = keke.name
                            it[driverName] = keke.driverName
                            it[description] = keke.description
                            it[price] = keke.price
                            it[imageUrl] = keke.imageUrl
                            it[isAvailable] = true
                            it[createdAt] = org.jetbrains.exposed.sql.javatime.CurrentTimestamp()
                        }
                    }
                    println("Seeded ${kekeList.size} keke vehicles with driver names and images.")
                }
            }
        }
    }
}
