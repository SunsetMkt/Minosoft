/*
 * Minosoft
 * Copyright (C) 2020-2022 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */
package de.bixilon.minosoft.data.text

import com.fasterxml.jackson.core.JacksonException
import de.bixilon.kutil.cast.CastUtil.unsafeCast
import de.bixilon.minosoft.Minosoft
import de.bixilon.minosoft.data.language.Translatable
import de.bixilon.minosoft.data.language.Translator
import de.bixilon.minosoft.data.registries.ResourceLocation
import de.bixilon.minosoft.gui.eros.util.JavaFXUtil.text
import de.bixilon.minosoft.util.json.Jackson
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.text.TextFlow

/**
 * Chat components are generally mutable while creating. Once you use it somewhere it is considered as non-mutable.
 */
interface ChatComponent {
    /**
     * @return Returns the message formatted with ANSI Formatting codes
     */
    val ansiColoredMessage: String

    /**
     * @return Returns the message formatted with minecraft formatting codes (§)
     */
    val legacyText: String

    /**
     * @return Returns the unformatted message
     */
    val message: String

    /**
     * @return Returns a list of Nodes, drawable in JavaFX (TextFlow)
     */
    fun getJavaFXText(nodes: ObservableList<Node>): ObservableList<Node>

    /**
     * @return Returns a list of Nodes, drawable in JavaFX (TextFlow)
     */
    val javaFXText: ObservableList<Node>
        get() = getJavaFXText(FXCollections.observableArrayList())

    val textFlow: TextFlow
        get() {
            val textFlow = TextFlow()
            textFlow.text = this
            return textFlow
        }

    /**
     * @return The current text component at a specific pointer (char offset)
     */
    fun getTextAt(pointer: Int): TextComponent

    /**
     * The length in chars
     */
    val length: Int


    fun cut(length: Int)


    fun strikethrough(): ChatComponent
    fun obfuscate(): ChatComponent
    fun bold(): ChatComponent
    fun underline(): ChatComponent
    fun italic(): ChatComponent
    fun setFallbackColor(color: RGBColor): ChatComponent

    companion object {
        val EMPTY = EmptyComponent

        @JvmOverloads
        fun of(raw: Any? = null, translator: Translator? = null, parent: TextComponent? = null, ignoreJson: Boolean = false, restrictedMode: Boolean = false): ChatComponent {
            if (raw == null) {
                return EMPTY
            }
            if (raw is ChatComponent) {
                return raw
            }
            if (raw is Translatable && raw !is ResourceLocation) {
                return (translator ?: Minosoft.LANGUAGE_MANAGER).translate(raw.translationKey, parent)
            }
            if (raw is Map<*, *>) {
                return BaseComponent(translator, parent, raw.unsafeCast(), restrictedMode)
            }
            val string = when (raw) {
                is List<*> -> {
                    val component = BaseComponent()
                    for (part in raw) {
                        component += of(part, translator, parent, restrictedMode = restrictedMode)
                    }
                    return component
                }
                else -> raw.toString()
            }
            if (!ignoreJson && string.startsWith('{')) {
                try {
                    return BaseComponent(translator, parent, Jackson.MAPPER.readValue(string, Jackson.JSON_MAP_TYPE), restrictedMode)
                } catch (ignored: JacksonException) {
                }
            }

            return BaseComponent(parent, string, restrictedMode)
        }

        fun String.chat(): ChatComponent {
            return of(this)
        }
    }
}
