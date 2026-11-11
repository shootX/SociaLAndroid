package com.ich.socialsave.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The v1 → v2 migration is checked against the schemas Room
 * exported: applying the migration's statements to the v1 schema must give exactly the v2 schema.
 */
class MigrationSchemaTest {
    private val dir = File("schemas/com.ich.socialsave.data.AppDatabase")
    private val v1 = schema(1)
    private val v2 = schema(2)
    private val sql = Migration1To2Sql.statements

    private val addColumn = Regex("""ALTER TABLE `(\w+)` ADD COLUMN (`(\w+)` (\w+) NOT NULL DEFAULT (\S+))""")
    private val createTable = Regex("""CREATE TABLE IF NOT EXISTS `(\w+)` (.*)""")

    @Test fun versions() {
        assertEquals(1, v1.getInt("version"))
        assertEquals(2, v2.getInt("version"))
    }

    @Test fun everyStatementIsUnderstood() {
        sql.forEach { statement ->
            assertTrue(
                "unexpected statement: $statement",
                addColumn.matches(statement) || createTable.matches(statement) ||
                    statement.matches(Regex("""UPDATE `(\w+)` SET `updatedAt` = `createdAt`""")),
            )
        }
    }

    @Test fun alteredTablesMatchV2() {
        val added = sql.mapNotNull { addColumn.matchEntire(it) }.groupBy({ it.groupValues[1] }, { it })
        val old = entities(v1)
        val new = entities(v2)
        assertEquals(setOf("saved_posts", "categories"), added.keys)

        for ((table, columns) in added) {
            val before = fields(old.getValue(table))
            val after = fields(new.getValue(table))
            val expected = before + columns.associate { match ->
                val (_, _, name, type, default) = match.destructured
                name to Field(type, notNull = true, default = default)
            }
            assertEquals("columns of $table", expected, after)
            // The full column definitions are what Room itself would create.
            columns.forEach { assertTrue(new.getValue(table).getString("createSql").contains(it.groupValues[2])) }
            // Indices (incl. the unique canonicalUrl index) are unchanged.
            assertEquals(indices(old.getValue(table)), indices(new.getValue(table)))
            // updatedAt is backfilled from createdAt.
            assertTrue(sql.contains("UPDATE `$table` SET `updatedAt` = `createdAt`"))
        }
    }

    @Test fun createdTablesMatchV2() {
        val created = sql.mapNotNull { createTable.matchEntire(it) }.associate { it.groupValues[1] to it.groupValues[0] }
        val new = entities(v2)
        assertEquals(setOf("deleted_posts", "deleted_categories"), created.keys)
        created.forEach { (table, statement) ->
            assertEquals(new.getValue(table).getString("createSql").replace("\${TABLE_NAME}", table), statement)
            assertTrue(indices(new.getValue(table)).isEmpty())
        }
    }

    @Test fun noTableIsMissing() {
        val touched = sql.mapNotNull { (addColumn.matchEntire(it) ?: createTable.matchEntire(it))?.groupValues?.get(1) }.toSet()
        val unchanged = entities(v1).keys - touched
        val unchangedInV2 = entities(v2).filterKeys { it in unchanged }
        unchangedInV2.forEach { (table, entity) -> assertEquals(entities(v1).getValue(table).toString(), entity.toString()) }
        assertEquals(entities(v2).keys, entities(v1).keys + touched)
    }

    private data class Field(val affinity: String, val notNull: Boolean, val default: String?)

    private fun schema(version: Int) = JSONObject(File(dir, "$version.json").readText()).getJSONObject("database")

    private fun entities(db: JSONObject): Map<String, JSONObject> {
        val array = db.getJSONArray("entities")
        return (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.getString("tableName") }
    }

    private fun fields(entity: JSONObject): Map<String, Field> {
        val array = entity.getJSONArray("fields")
        return (0 until array.length()).map { array.getJSONObject(it) }.associate {
            it.getString("columnName") to Field(
                it.getString("affinity"),
                it.optBoolean("notNull", false),
                if (it.has("defaultValue")) it.getString("defaultValue") else null,
            )
        }
    }

    private fun indices(entity: JSONObject): List<String> {
        val array = entity.optJSONArray("indices") ?: JSONArray()
        return (0 until array.length()).map { array.getJSONObject(it).getString("createSql") }.sorted()
    }
}
