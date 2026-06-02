package com.snupai.trinkspiel.data

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CardUserEntity::class,
        DrinkLevelOneEntryEntity::class,
        DrinkLevelTwoEntryEntity::class,
        DrinkLevelThreeEntryEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drinkEntryDao(): DrinkEntryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE drink_entries ADD COLUMN category TEXT NOT NULL DEFAULT 'challenge'"
                )
                db.execSQL(
                    "ALTER TABLE drink_entries ADD COLUMN packName TEXT NOT NULL DEFAULT 'Eigene Karten'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE drink_entries ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE drink_entries ADD COLUMN questionLevel INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE drink_entries ADD COLUMN ownerName TEXT NOT NULL DEFAULT 'Lokal'"
                )
                db.execSQL(
                    "ALTER TABLE drink_entries ADD COLUMN contributorName TEXT NOT NULL DEFAULT 'Lokal'"
                )
                db.execSQL(
                    """
                    UPDATE drink_entries
                    SET questionLevel = CASE
                        WHEN drinks <= 1 THEN 1
                        WHEN drinks <= 3 THEN 2
                        ELSE 3
                    END
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLevelTable(db, "drink_level_1_entries")
                createLevelTable(db, "drink_level_2_entries")
                createLevelTable(db, "drink_level_3_entries")
                db.execSQL(
                    """
                    INSERT INTO drink_level_1_entries (
                        id, text, drinks, category, packName, isEnabled,
                        questionLevel, ownerName, contributorName
                    )
                    SELECT
                        id, text, drinks, category, packName, isEnabled,
                        1, ownerName, contributorName
                    FROM drink_entries
                    WHERE questionLevel NOT IN (2, 3)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO drink_level_2_entries (
                        id, text, drinks, category, packName, isEnabled,
                        questionLevel, ownerName, contributorName
                    )
                    SELECT
                        id, text, drinks, category, packName, isEnabled,
                        2, ownerName, contributorName
                    FROM drink_entries
                    WHERE questionLevel = 2
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO drink_level_3_entries (
                        id, text, drinks, category, packName, isEnabled,
                        questionLevel, ownerName, contributorName
                    )
                    SELECT
                        id, text, drinks, category, packName, isEnabled,
                        3, ownerName, contributorName
                    FROM drink_entries
                    WHERE questionLevel = 3
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE drink_entries")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createCardUsersTable(db)
                addUserIdColumns(db, "drink_level_1_entries")
                addUserIdColumns(db, "drink_level_2_entries")
                addUserIdColumns(db, "drink_level_3_entries")
                updateLevelTableUserIds(db, "drink_level_1_entries")
                updateLevelTableUserIds(db, "drink_level_2_entries")
                updateLevelTableUserIds(db, "drink_level_3_entries")
                db.execSQL(
                    "INSERT OR IGNORE INTO card_users (`id`, `displayName`) VALUES ('local', 'Lokal')"
                )
                insertUsersFromLevelTables(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addPendingReviewColumn(db, "drink_level_1_entries")
                addPendingReviewColumn(db, "drink_level_2_entries")
                addPendingReviewColumn(db, "drink_level_3_entries")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addSyncColumns(db, "drink_level_1_entries")
                addSyncColumns(db, "drink_level_2_entries")
                addSyncColumns(db, "drink_level_3_entries")
            }
        }

        private fun createLevelTable(db: SupportSQLiteDatabase, tableName: String) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `$tableName` (
                    `id` INTEGER NOT NULL,
                    `text` TEXT NOT NULL,
                    `drinks` INTEGER NOT NULL,
                    `category` TEXT NOT NULL,
                    `packName` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL,
                    `questionLevel` INTEGER NOT NULL,
                    `ownerName` TEXT NOT NULL,
                    `contributorName` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }

        private fun createCardUsersTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `card_users` (
                    `id` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }

        private fun addUserIdColumns(db: SupportSQLiteDatabase, tableName: String) {
            db.execSQL(
                "ALTER TABLE `$tableName` ADD COLUMN `ownerUserId` TEXT NOT NULL DEFAULT 'local'"
            )
            db.execSQL(
                "ALTER TABLE `$tableName` ADD COLUMN `contributorUserId` TEXT NOT NULL DEFAULT 'local'"
            )
        }

        private fun addPendingReviewColumn(db: SupportSQLiteDatabase, tableName: String) {
            db.execSQL(
                "ALTER TABLE `$tableName` ADD COLUMN `isPendingReview` INTEGER NOT NULL DEFAULT 0"
            )
        }

        private fun addSyncColumns(db: SupportSQLiteDatabase, tableName: String) {
            db.execSQL(
                "ALTER TABLE `$tableName` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE `$tableName` ADD COLUMN `updatedAtMillis` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE `$tableName` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'local'"
            )
        }

        private fun updateLevelTableUserIds(db: SupportSQLiteDatabase, tableName: String) {
            db.execSQL(
                """
                UPDATE `$tableName`
                SET ownerUserId = CASE
                        WHEN trim(ownerName) = '' OR lower(trim(ownerName)) = 'lokal' THEN 'local'
                        ELSE 'local_user_' || lower(hex(lower(trim(ownerName))))
                    END,
                    contributorUserId = CASE
                        WHEN trim(contributorName) = '' OR lower(trim(contributorName)) = 'lokal' THEN 'local'
                        ELSE 'local_user_' || lower(hex(lower(trim(contributorName))))
                    END
                """.trimIndent()
            )
        }

        private fun insertUsersFromLevelTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR REPLACE INTO card_users (`id`, `displayName`)
                SELECT ownerUserId, ownerName FROM drink_level_1_entries WHERE trim(ownerName) != ''
                UNION
                SELECT contributorUserId, contributorName FROM drink_level_1_entries WHERE trim(contributorName) != ''
                UNION
                SELECT ownerUserId, ownerName FROM drink_level_2_entries WHERE trim(ownerName) != ''
                UNION
                SELECT contributorUserId, contributorName FROM drink_level_2_entries WHERE trim(contributorName) != ''
                UNION
                SELECT ownerUserId, ownerName FROM drink_level_3_entries WHERE trim(ownerName) != ''
                UNION
                SELECT contributorUserId, contributorName FROM drink_level_3_entries WHERE trim(contributorName) != ''
                """.trimIndent()
            )
        }
    }
}
