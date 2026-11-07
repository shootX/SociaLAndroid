package com.ich.socialsave.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: sync columns and tombstone tables. SQLite can't use another column as an ADD COLUMN default,
 * so `updatedAt` is added as 0 and backfilled from `createdAt`. Every existing row starts dirty, so the
 * library is uploaded on the first sign-in. The statements live in a plain object so a JVM test can check
 * them against the exported schemas (`app/schemas`).
 */
object Migration1To2Sql {
    val statements = listOf(
        "ALTER TABLE `saved_posts` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `saved_posts` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1",
        "UPDATE `saved_posts` SET `updatedAt` = `createdAt`",
        "ALTER TABLE `categories` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `categories` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1",
        "UPDATE `categories` SET `updatedAt` = `createdAt`",
        "CREATE TABLE IF NOT EXISTS `deleted_posts` (`id` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `deleted_categories` (`id` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Migration1To2Sql.statements.forEach(db::execSQL)
    }
}
