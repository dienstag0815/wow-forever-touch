package app.gamenative.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImportPlanTest {
    private fun incoming(id: Int, name: String, extra: String = """"elements":[]""") =
        BackupProfile(id, name, """{"id":$id,"name":"$name",$extra}""")

    private fun existing(id: Int, name: String, extra: String? = """"elements":[]""") =
        ExistingProfile(id, name, extra?.let { """{"id":$id,"name":"$name",$it}""" })

    private fun idOf(json: String) = JSONObject(json).getInt("id")

    @Test
    fun `a profile that does not exist yet is created with a new id`() {
        val plan = BackupImportPlan.plan(listOf(incoming(1, "A")), listOf(existing(4, "B")), ConflictPolicy.REPLACE)

        val step = plan.steps.single()
        assertEquals(ProfileAction.CREATE, step.action)
        assertEquals(5, step.targetId)
        assertEquals(5, idOf(step.json))
        assertEquals("A", step.targetName)
    }

    @Test
    fun `an empty device starts at id 0`() {
        val plan = BackupImportPlan.plan(listOf(incoming(9, "A"), incoming(2, "B")), emptyList(), ConflictPolicy.REPLACE)
        assertEquals(listOf(0, 1), plan.steps.map { it.targetId })
    }

    @Test
    fun `same name and same content is identical even with another id and key order`() {
        val device = ExistingProfile(5, "A", """{"name":"A","elements":[{"x":1,"y":2}],"id":5}""")
        val backup = BackupProfile(1, "A", """{"id":1,"name":"A","elements":[{"y":2.0,"x":1}]}""")

        val plan = BackupImportPlan.plan(listOf(backup), listOf(device), ConflictPolicy.REPLACE)

        assertEquals(ProfileAction.IDENTICAL, plan.steps.single().action)
        assertTrue(plan.writes.isEmpty())
        assertTrue(BackupImportPlan.conflicts(listOf(backup), listOf(device)).isEmpty())
    }

    @Test
    fun `replace keeps the id of the profile on the device`() {
        val plan = BackupImportPlan.plan(
            listOf(incoming(1, "A", """"elements":[1]""")),
            listOf(existing(8, "A")),
            ConflictPolicy.REPLACE,
        )

        val step = plan.steps.single()
        assertEquals(ProfileAction.REPLACE, step.action)
        assertEquals(8, step.targetId)
        assertEquals(8, idOf(step.json))
        assertEquals("A", JSONObject(step.json).getString("name"))
    }

    @Test
    fun `keep both adds the profile under a new name and id`() {
        val plan = BackupImportPlan.plan(
            listOf(incoming(1, "A", """"elements":[1]""")),
            listOf(existing(8, "A")),
            ConflictPolicy.KEEP_BOTH,
        )

        val step = plan.steps.single()
        assertEquals(ProfileAction.ADD_RENAMED, step.action)
        assertEquals("A (Backup)", step.targetName)
        assertEquals(9, step.targetId)
        assertEquals("A (Backup)", JSONObject(step.json).getString("name"))
        assertEquals(9, idOf(step.json))
    }

    @Test
    fun `keep both counts up when the backup name is taken too`() {
        assertEquals("A (Backup)", BackupImportPlan.uniqueName("A", listOf("A")))
        assertEquals("A (Backup 2)", BackupImportPlan.uniqueName("A", listOf("A", "A (Backup)")))
        assertEquals("A (Backup 3)", BackupImportPlan.uniqueName("A", listOf("A", "A (Backup)", "A (Backup 2)")))

        val plan = BackupImportPlan.plan(
            listOf(incoming(1, "A", """"elements":[1]""")),
            listOf(existing(1, "A"), existing(2, "A (Backup)")),
            ConflictPolicy.KEEP_BOTH,
        )
        assertEquals("A (Backup 2)", plan.steps.single().targetName)
        assertEquals(3, plan.steps.single().targetId)
    }

    @Test
    fun `a name twice in the backup gives the second one its own profile`() {
        val plan = BackupImportPlan.plan(
            listOf(incoming(1, "A", """"elements":[1]"""), incoming(2, "A", """"elements":[2]""")),
            listOf(existing(8, "A")),
            ConflictPolicy.REPLACE,
        )

        assertEquals(ProfileAction.REPLACE, plan.steps[0].action)
        assertEquals(ProfileAction.ADD_RENAMED, plan.steps[1].action)
        assertEquals("A (Backup)", plan.steps[1].targetName)
        // Das erste ersetzt Id 8, das zweite bekommt eine freie Id.
        assertEquals(listOf(8, 9), plan.steps.map { it.targetId })
    }

    @Test
    fun `a name twice in the backup on an empty device creates two profiles`() {
        val plan = BackupImportPlan.plan(
            listOf(incoming(1, "A"), incoming(2, "A")),
            emptyList(),
            ConflictPolicy.REPLACE,
        )

        assertEquals(ProfileAction.CREATE, plan.steps[0].action)
        assertEquals(ProfileAction.ADD_RENAMED, plan.steps[1].action)
        assertEquals(setOf("A", "A (Backup)"), plan.steps.map { it.targetName }.toSet())
        assertEquals(2, plan.steps.map { it.targetId }.toSet().size)
    }

    @Test
    fun `new ids never collide with existing ones or with each other`() {
        val device = listOf(existing(0, "X"), existing(3, "Y"))
        val plan = BackupImportPlan.plan(
            listOf(incoming(0, "A"), incoming(3, "B"), incoming(1, "C")),
            device,
            ConflictPolicy.KEEP_BOTH,
        )

        val ids = plan.steps.map { it.targetId }
        assertEquals(3, ids.toSet().size)
        assertTrue(ids.none { it in setOf(0, 3) })
    }

    @Test
    fun `conflicts lists only profiles with the same name and other content`() {
        val list = BackupImportPlan.conflicts(
            listOf(incoming(1, "A", """"elements":[1]"""), incoming(2, "B"), incoming(3, "C")),
            listOf(existing(1, "A"), existing(2, "B")),
        )
        assertEquals(listOf("A"), list)
    }

    @Test
    fun `an unreadable profile on the device counts as a conflict`() {
        val list = BackupImportPlan.conflicts(listOf(incoming(1, "A")), listOf(existing(1, "A", extra = null)))
        assertEquals(listOf("A"), list)
    }

    @Test
    fun `same content ignores id and spacing but not values`() {
        assertTrue(BackupImportPlan.sameContent("""{"id":1,"a":[1,2]}""", """{ "a" : [1, 2], "id": 9 }"""))
        assertFalse(BackupImportPlan.sameContent("""{"id":1,"a":[1,2]}""", """{"id":1,"a":[2,1]}"""))
        assertFalse(BackupImportPlan.sameContent("""{"id":1,"a":1}""", """{"id":1,"a":"1"}"""))
        assertFalse(BackupImportPlan.sameContent(null, "{}"))
        assertFalse(BackupImportPlan.sameContent("kein json", "{}"))
    }

    @Test
    fun `plan counts actions`() {
        val plan = BackupImportPlan.plan(
            listOf(incoming(1, "A"), incoming(2, "B", """"elements":[1]"""), incoming(3, "C")),
            listOf(existing(1, "A"), existing(2, "B")),
            ConflictPolicy.REPLACE,
        )
        assertEquals(1, plan.count(ProfileAction.IDENTICAL))
        assertEquals(1, plan.count(ProfileAction.REPLACE))
        assertEquals(1, plan.count(ProfileAction.CREATE))
        assertEquals(2, plan.writes.size)
    }
}
