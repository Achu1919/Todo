package dev.saikrishna.todo.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** v0.13.3: the profile circle's mark is the Name's first grapheme cluster, uppercased when sensible. */
class ProfileInitialTest {
    private fun i(s: String, l: Locale = Locale.ENGLISH) = profileInitial(s, l)

    @Test fun latinLetterIsUppercased() {
        assertEquals("S", i("sai"))
        assertEquals("S", i("Sai Krishna"))
        assertEquals("É", i("  émile "))
        assertEquals("E\u0301", i("e\u0301mile")) // decomposed: base + combining acute stays one mark
        assertEquals("Ω", i("ωmega"))
        assertEquals("İ", i("istanbul", Locale.forLanguageTag("tr")))
    }

    @Test fun uppercaseThatWouldSplitIsSkipped() {
        assertEquals("ß", i("ßen")) // "SS" would be two letters
    }

    @Test fun nonLatinFirstGrapheme() {
        assertEquals("张", i("张伟"))
        assertEquals("東", i("東京"))
        assertEquals("सु", i("सुमन")) // consonant + vowel sign is one grapheme
        assertEquals("ع", i("علي"))
        assertEquals("Д", i("дмитрий"))
    }

    @Test fun emojiStayWhole() {
        assertEquals("👩‍💻", i("👩‍💻 Dev"))
        assertEquals("👍🏽", i("👍🏽yo"))
        assertEquals("🇮🇳", i("🇮🇳 India"))
        assertEquals("😀", i("😀"))
    }

    @Test fun blankIsNull() {
        assertNull(i(""))
        assertNull(i("   "))
    }
}
