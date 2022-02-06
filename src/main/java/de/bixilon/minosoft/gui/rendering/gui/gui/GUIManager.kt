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

package de.bixilon.minosoft.gui.rendering.gui.gui

import de.bixilon.kutil.cast.CastUtil.unsafeCast
import de.bixilon.kutil.time.TimeUtil
import de.bixilon.minosoft.config.key.KeyAction
import de.bixilon.minosoft.config.key.KeyBinding
import de.bixilon.minosoft.config.key.KeyCodes
import de.bixilon.minosoft.gui.rendering.gui.GUIElement
import de.bixilon.minosoft.gui.rendering.gui.GUIElementDrawer
import de.bixilon.minosoft.gui.rendering.gui.GUIRenderer
import de.bixilon.minosoft.gui.rendering.gui.elements.Pollable
import de.bixilon.minosoft.gui.rendering.gui.gui.screen.menu.pause.PauseMenu
import de.bixilon.minosoft.gui.rendering.gui.hud.Initializable
import de.bixilon.minosoft.gui.rendering.gui.hud.elements.HUDBuilder
import de.bixilon.minosoft.gui.rendering.gui.hud.elements.LayoutedGUIElement
import de.bixilon.minosoft.gui.rendering.input.InputHandler
import de.bixilon.minosoft.gui.rendering.renderer.Drawable
import de.bixilon.minosoft.gui.rendering.system.window.KeyChangeTypes
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition
import de.bixilon.minosoft.util.KUtil.toResourceLocation
import glm_.vec2.Vec2i

class GUIManager(
    override val guiRenderer: GUIRenderer,
) : Initializable, InputHandler, GUIElementDrawer {
    private val elementCache: MutableMap<GUIBuilder<*>, GUIElement> = mutableMapOf()
    var elementOrder: MutableList<GUIElement> = mutableListOf()
    private val renderWindow = guiRenderer.renderWindow
    internal var paused = false
    override var lastTickTime: Long = -1L

    override fun init() {
        for (element in elementCache.values) {
            element.init()
        }
    }

    override fun postInit() {
        renderWindow.inputHandler.registerKeyCallback("minosoft:back".toResourceLocation(),
            KeyBinding(
                mapOf(
                    KeyAction.RELEASE to setOf(KeyCodes.KEY_ESCAPE),
                ),
                ignoreConsumer = true,
            )) { popOrPause() }


        for (element in elementCache.values) {
            element.postInit()
            if (element is LayoutedGUIElement<*>) {
                element.initMesh()
            }
        }
    }

    fun onMatrixChange() {
        for (element in elementCache.values) {
            // ToDo: Just the current active one
            if (element is LayoutedGUIElement<*>) {
                element.elementLayout.forceSilentApply()
            }
            element.apply()
        }
    }

    fun draw() {
        val element = elementOrder.firstOrNull() ?: return
        if (!element.enabled) {
            return
        }
        val time = TimeUtil.time
        if (time - lastTickTime > ProtocolDefinition.TICK_TIME) {
            element.tick()
            if (element is Pollable) {
                if (element.poll()) {
                    element.apply()
                }
            }

            lastTickTime = time
        }

        if (element is Drawable && !element.skipDraw) {
            element.draw()
        }
        if (element is LayoutedGUIElement<*>) {
            element.prepare()
        }

        guiRenderer.setup()
        if (element !is LayoutedGUIElement<*> || !element.enabled || element.mesh.data.isEmpty) {
            return
        }
        element.mesh.draw()
    }

    fun pause(pause: Boolean = !paused) {
        if (pause == paused) {
            return
        }

        paused = pause
        if (pause) {
            if (elementOrder.isNotEmpty()) {
                return
            }
            open(PauseMenu)
        } else {
            clear()
        }
    }

    override fun onCharPress(char: Int) {
        elementOrder.firstOrNull()?.onCharPress(char)
    }

    override fun onMouseMove(position: Vec2i) {
        elementOrder.firstOrNull()?.onMouseMove(position)
    }

    override fun onKeyPress(type: KeyChangeTypes, key: KeyCodes) {
        elementOrder.firstOrNull()?.onKeyPress(type, key)
    }

    fun open(builder: GUIBuilder<*>) {
        clear()
        val element = this[builder]
        elementOrder += element
        element.onOpen()

        renderWindow.inputHandler.inputHandler = guiRenderer
    }

    fun popOrPause() {
        if (elementOrder.isEmpty()) {
            return pause()
        }
        pop()
    }

    fun push(builder: GUIBuilder<*>) {
        if (elementOrder.isEmpty()) {
            renderWindow.inputHandler.inputHandler = guiRenderer
        }
        val element = this[builder]
        elementOrder.firstOrNull()?.onHide()
        elementOrder.add(0, element)
        element.onOpen()
    }

    fun pop() {
        val previous = elementOrder.removeFirstOrNull() ?: return
        previous.onClose()
        if (elementOrder.isEmpty()) {
            renderWindow.inputHandler.inputHandler = null
        }
    }

    fun clear() {
        for (element in elementOrder) {
            element.onClose()
        }
        elementOrder.clear()
        renderWindow.inputHandler.inputHandler = null
    }

    operator fun <T : GUIElement> get(builder: GUIBuilder<T>): T {
        return elementCache.getOrPut(builder) {
            if (builder is HUDBuilder<*>) {
                guiRenderer.hud[builder]?.let { return it.unsafeCast() }
            }
            val element = builder.build(guiRenderer)
            element.init()
            element.postInit()
            return element
        }.unsafeCast() // init mesh
    }
}
