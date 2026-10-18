package tv.lumo.android.core.common.locale

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Q6: which interface language applies, and how each one is named (S13-04). */
class InterfaceLanguageTest {

    @Test
    fun `an explicit choice wins over the system`() {
        assertThat(InterfaceLanguage.effective(explicit = "fr", system = "en")).isEqualTo("fr")
        assertThat(InterfaceLanguage.effective(explicit = "en", system = "fr")).isEqualTo("en")
    }

    @Test
    fun `without a choice, a supported system language, else English`() {
        assertThat(InterfaceLanguage.effective(explicit = null, system = "fr-CA")).isEqualTo("fr")
        assertThat(InterfaceLanguage.effective(explicit = null, system = "de")).isEqualTo("en")
        assertThat(InterfaceLanguage.effective(explicit = null, system = null)).isEqualTo("en")
    }

    @Test
    fun `an unsupported explicit value is ignored rather than trusted`() {
        assertThat(InterfaceLanguage.effective(explicit = "de", system = "fr")).isEqualTo("fr")
        assertThat(InterfaceLanguage.supportedOrNull("FR_fr")).isEqualTo("fr")
        assertThat(InterfaceLanguage.supportedOrNull("")).isNull()
    }

    @Test
    fun `each language is named in itself`() {
        assertThat(InterfaceLanguage.nativeName("fr")).isEqualTo("Français")
        assertThat(InterfaceLanguage.nativeName("en")).isEqualTo("English")
        assertThat(InterfaceLanguage.SUPPORTED).containsExactly("fr", "en").inOrder()
    }
}
