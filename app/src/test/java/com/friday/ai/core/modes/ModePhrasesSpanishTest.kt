package com.friday.ai.core.modes

import com.friday.ai.domain.model.CommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModePhrasesSpanishTest {

    @Test
    fun `create run list describe and delete Spanish modes`() {
        assertEquals(
            CommandResult.Mode.Create("descanso", "baja el brillo y activa no molestar"),
            ModePhrases.parse("Oxxi, crea el modo descanso: baja el brillo y activa No molestar")
        )
        assertEquals(CommandResult.Mode.Run("descanso"), ModePhrases.parse("activa el modo descanso"))
        assertEquals(CommandResult.Mode.ListAll, ModePhrases.parse("qué modos tengo"))
        assertEquals(CommandResult.Mode.Describe("descanso"), ModePhrases.parse("qué hace el modo descanso"))
        assertEquals(CommandResult.Mode.Delete("descanso"), ModePhrases.parse("elimina el modo descanso"))
    }

    @Test
    fun `append and remove steps from Spanish modes`() {
        assertEquals(
            CommandResult.Mode.AddTo("descanso baja el brillo"),
            ModePhrases.parse("añade al modo descanso baja el brillo")
        )
        assertEquals(
            CommandResult.Mode.RemoveFrom("baja el brillo", "descanso"),
            ModePhrases.parse("quita baja el brillo del modo descanso")
        )
    }

    @Test
    fun `unrelated phrase is not mistaken for a mode`() {
        assertNull(ModePhrases.parse("Oxxi, ¿qué tiempo hace hoy?"))
    }
}
