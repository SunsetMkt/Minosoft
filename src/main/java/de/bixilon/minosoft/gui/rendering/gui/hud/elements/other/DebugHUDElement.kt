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

package de.bixilon.minosoft.gui.rendering.gui.hud.elements.other

import de.bixilon.kutil.math.simple.DoubleMath.rounded10
import de.bixilon.kutil.unit.UnitFormatter.formatBytes
import de.bixilon.minosoft.config.key.KeyAction
import de.bixilon.minosoft.config.key.KeyBinding
import de.bixilon.minosoft.config.key.KeyCodes
import de.bixilon.minosoft.data.direction.Directions
import de.bixilon.minosoft.data.registries.ResourceLocation
import de.bixilon.minosoft.data.registries.other.game.event.handlers.gamemode.GamemodeChangeEvent
import de.bixilon.minosoft.data.text.BaseComponent
import de.bixilon.minosoft.data.text.ChatColors
import de.bixilon.minosoft.data.text.TextComponent
import de.bixilon.minosoft.data.world.Chunk
import de.bixilon.minosoft.gui.rendering.gui.GUIRenderer
import de.bixilon.minosoft.gui.rendering.gui.elements.Element
import de.bixilon.minosoft.gui.rendering.gui.elements.HorizontalAlignments
import de.bixilon.minosoft.gui.rendering.gui.elements.LayoutedElement
import de.bixilon.minosoft.gui.rendering.gui.elements.layout.RowLayout
import de.bixilon.minosoft.gui.rendering.gui.elements.layout.grid.GridGrow
import de.bixilon.minosoft.gui.rendering.gui.elements.layout.grid.GridLayout
import de.bixilon.minosoft.gui.rendering.gui.elements.spacer.LineSpacerElement
import de.bixilon.minosoft.gui.rendering.gui.elements.text.AutoTextElement
import de.bixilon.minosoft.gui.rendering.gui.elements.text.TextElement
import de.bixilon.minosoft.gui.rendering.gui.gui.LayoutedGUIElement
import de.bixilon.minosoft.gui.rendering.gui.hud.Initializable
import de.bixilon.minosoft.gui.rendering.gui.hud.elements.HUDBuilder
import de.bixilon.minosoft.gui.rendering.gui.mesh.GUIVertexConsumer
import de.bixilon.minosoft.gui.rendering.gui.mesh.GUIVertexOptions
import de.bixilon.minosoft.gui.rendering.modding.events.ResizeWindowEvent
import de.bixilon.minosoft.gui.rendering.particle.ParticleRenderer
import de.bixilon.minosoft.gui.rendering.util.vec.vec2.Vec2iUtil.EMPTY
import de.bixilon.minosoft.gui.rendering.world.WorldRenderer
import de.bixilon.minosoft.modding.event.events.DifficultyChangeEvent
import de.bixilon.minosoft.modding.event.events.TimeChangeEvent
import de.bixilon.minosoft.modding.event.invoker.CallbackEventInvoker
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition
import de.bixilon.minosoft.terminal.RunConfiguration
import de.bixilon.minosoft.util.GitInfo
import de.bixilon.minosoft.util.KUtil.format
import de.bixilon.minosoft.util.KUtil.toResourceLocation
import de.bixilon.minosoft.util.SystemInformation
import glm_.vec2.Vec2i
import glm_.vec4.Vec4i
import kotlin.math.abs

class DebugHUDElement(guiRenderer: GUIRenderer) : Element(guiRenderer), LayoutedElement, Initializable {
     val connection = renderWindow.connection
    private val layout = GridLayout(guiRenderer, Vec2i(3, 1)).apply { parent = this@DebugHUDElement }
    override val layoutOffset: Vec2i = Vec2i.EMPTY

    init {
        layout.columnConstraints[0].apply {
            grow = GridGrow.NEVER
        }
        layout.columnConstraints[2].apply {
            grow = GridGrow.NEVER
            alignment = HorizontalAlignments.RIGHT
        }

        apply()
    }


    override fun init() {
        layout[Vec2i(0, 0)] = initLeft()
        layout[Vec2i(2, 0)] = initRight()

        this.prefMaxSize = Vec2i(-1, Int.MAX_VALUE)
        this.ignoreDisplaySize = true
    }

    private fun initLeft(): Element {
        val layout = RowLayout(guiRenderer)
        layout.margin = Vec4i(2)
        layout += TextElement(guiRenderer, TextComponent(RunConfiguration.VERSION_STRING, ChatColors.RED))
        layout += AutoTextElement(guiRenderer, 1) { "FPS ${renderWindow.renderStats.smoothAvgFPS.rounded10}" }
        renderWindow.renderer[WorldRenderer]?.apply {
            layout += AutoTextElement(guiRenderer, 1) { "C v=$visibleSize, m=$loadedMeshesSize, cQ=$culledQueuedSize, q=$queueSize, pT=$preparingTasksSize/$maxPreparingTasks, l=$meshesToLoadSize/$maxMeshesToLoad, w=${connection.world.chunks.size}" }
        }
        layout += AutoTextElement(guiRenderer, 1) { "E t=${connection.world.entities.size}" }

        renderWindow.renderer[ParticleRenderer]?.apply {
            layout += AutoTextElement(guiRenderer, 1) { "P t=$size" }
        }

        val audioProfile = connection.profiles.audio

        layout += AutoTextElement(guiRenderer, 1) {
            BaseComponent().apply {
                this += "S "
                if (connection.profiles.audio.skipLoading || !audioProfile.enabled) {
                    this += "§cdisabled"
                } else {
                    val audioPlayer = renderWindow.rendering.audioPlayer

                    this += audioPlayer.availableSources
                    this += " / "
                    this += audioPlayer.sourcesCount
                }
            }
        }

        layout += LineSpacerElement(guiRenderer)

        layout += TextElement(guiRenderer, BaseComponent("Account ", connection.account.username))
        layout += TextElement(guiRenderer, BaseComponent("Address ", connection.address))
        layout += TextElement(guiRenderer, BaseComponent("Network version ", connection.version))
        layout += TextElement(guiRenderer, BaseComponent("Server brand ", connection.serverInfo.brand))

        layout += LineSpacerElement(guiRenderer)


        connection.player.apply {
            // ToDo: Only update when the position changes
            layout += AutoTextElement(guiRenderer, 1) { with(physics.position) { "XYZ ${x.format()} / ${y.format()} / ${z.format()}" } }
            layout += AutoTextElement(guiRenderer, 1) { with(physics.blockPosition) { "Block $x $y $z" } }
            layout += AutoTextElement(guiRenderer, 1) { with(physics) { "Chunk $inChunkSectionPosition in (${chunkPosition.x} $sectionHeight ${chunkPosition.y})" } }
            layout += AutoTextElement(guiRenderer, 1) {
                val text = BaseComponent("Facing ")

                Directions.byDirection(guiRenderer.renderWindow.camera.matrixHandler.cameraFront).apply {
                    text += this
                    text += " "
                    text += vector
                }

                guiRenderer.renderWindow.connection.player.physics.rotation.apply {
                    text += " yaw=${yaw.rounded10}, pitch=${pitch.rounded10}"
                }

                text
            }
        }

        layout += LineSpacerElement(guiRenderer)

        val chunk = connection.world[connection.player.physics.chunkPosition]

        if (chunk == null) {
            layout += DebugWorldInfo(guiRenderer)
        }

        layout += LineSpacerElement(guiRenderer)

        layout += TextElement(guiRenderer, BaseComponent("Gamemode ", connection.player.gamemode)).apply {
            connection.registerEvent(CallbackEventInvoker.of<GamemodeChangeEvent> {
                text = BaseComponent("Gamemode ", it.gamemode)
            })
        }

        layout += TextElement(guiRenderer, BaseComponent("Difficulty ", connection.world.difficulty, ", locked=", connection.world.difficultyLocked)).apply {
            connection.registerEvent(CallbackEventInvoker.of<DifficultyChangeEvent> {
                text = BaseComponent("Difficulty ", it.difficulty, ", locked=", it.locked)
            })
        }

        layout += TextElement(guiRenderer, "Time TBA").apply {
            connection.registerEvent(CallbackEventInvoker.of<TimeChangeEvent> {
                text = BaseComponent("Time ", abs(it.time % ProtocolDefinition.TICKS_PER_DAY), ", moving=", it.time >= 0, ", day=", abs(it.age) / ProtocolDefinition.TICKS_PER_DAY)
            })
        }

        layout += AutoTextElement(guiRenderer, 1) { "Fun effect: " + (renderWindow.framebufferManager.world.`fun`.effect?.resourceLocation ?: "None") }

        return layout
    }

    private fun initRight(): Element {
        val layout = RowLayout(guiRenderer, HorizontalAlignments.RIGHT)
        layout.margin = Vec4i(2)
        layout += TextElement(guiRenderer, "Java ${Runtime.version()} ${System.getProperty("sun.arch.data.model")}bit", HorizontalAlignments.RIGHT)
        layout += TextElement(guiRenderer, "OS ${SystemInformation.OS_TEXT}", HorizontalAlignments.RIGHT)

        layout += LineSpacerElement(guiRenderer)

        SystemInformation.RUNTIME.apply {
            layout += AutoTextElement(guiRenderer, 1) {
                val total = maxMemory()
                val used = totalMemory() - freeMemory()
                "Memory ${(used * 100.0 / total).rounded10}% ${used.formatBytes()} / ${total.formatBytes()}"
            }
            layout += AutoTextElement(guiRenderer, 1) {
                val total = maxMemory()
                val allocated = totalMemory()
                "Allocated ${(allocated * 100.0 / total).rounded10}% ${allocated.formatBytes()} / ${total.formatBytes()}"
            }
        }

        layout += LineSpacerElement(guiRenderer)

        layout += TextElement(guiRenderer, "CPU ${SystemInformation.PROCESSOR_TEXT}", HorizontalAlignments.RIGHT)
        layout += TextElement(guiRenderer, "Memory ${SystemInformation.SYSTEM_MEMORY.formatBytes()}")


        layout += LineSpacerElement(guiRenderer)

        layout += TextElement(guiRenderer, "Display TBA", HorizontalAlignments.RIGHT).apply {
            guiRenderer.renderWindow.connection.registerEvent(CallbackEventInvoker.of<ResizeWindowEvent> {
                text = "Display ${it.size.x}x${it.size.y}"
            })
        }

        renderWindow.renderSystem.apply {
            layout += TextElement(guiRenderer, "GPU $gpuType", HorizontalAlignments.RIGHT)
            layout += TextElement(guiRenderer, "Version $version", HorizontalAlignments.RIGHT)
        }

        if (GitInfo.IS_INITIALIZED) {
            layout += LineSpacerElement(guiRenderer)

            GitInfo.apply {
                layout += TextElement(guiRenderer, "Git $GIT_COMMIT_ID_ABBREV: $GIT_COMMIT_MESSAGE_SHORT", HorizontalAlignments.RIGHT)
            }
        }

        layout += LineSpacerElement(guiRenderer)

        layout += TextElement(guiRenderer, "${connection.size}x listeners", HorizontalAlignments.RIGHT)

        layout += LineSpacerElement(guiRenderer)

        renderWindow.camera.targetHandler.apply {
            layout += AutoTextElement(guiRenderer, 1, HorizontalAlignments.RIGHT) {
                // ToDo: Tags
                target ?: "No target"
            }
        }
        return layout
    }

    private inner class DebugWorldInfo(guiRenderer: GUIRenderer) : RowLayout(guiRenderer) {
        private var lastChunk: Chunk? = null
        private val world = guiRenderer.renderWindow.connection.world
        private val entity = guiRenderer.renderWindow.connection.player

        init {
            showWait()
        }

        private fun showWait() {
            clear()
            this += TextElement(guiRenderer, "Waiting for chunk...")
        }

        private fun updateInformation() {
            val physics = entity.physics
            val chunk = world[physics.chunkPosition]

            if ((chunk == null && lastChunk == null) || (chunk != null && lastChunk != null)) {
                // No update, elements will update themselves
                return
            }
            if (chunk == null) {
                lastChunk = null
                showWait()
                return
            }
            clear()

            this@DebugWorldInfo += AutoTextElement(guiRenderer, 1) { BaseComponent("Sky properties ", connection.world.dimension?.skyProperties) }
            this@DebugWorldInfo += AutoTextElement(guiRenderer, 1) { BaseComponent("Biome ", connection.world.getBiome(physics.blockPosition)) }
            this@DebugWorldInfo += AutoTextElement(guiRenderer, 1) { with(connection.world.getLight(physics.blockPosition)) { BaseComponent("Light block=", (this and 0x0F), ", sky=", ((this and 0xF0) shr 4)) } }
            this@DebugWorldInfo += AutoTextElement(guiRenderer, 1) { BaseComponent("Fully loaded: ", world[physics.chunkPosition]?.isFullyLoaded) }

            lastChunk = chunk
        }

        override fun tick() {
            // ToDo: Make event driven
            updateInformation()

            super.tick()
        }
    }

    override fun forceRender(offset: Vec2i, consumer: GUIVertexConsumer, options: GUIVertexOptions?) {
        layout.forceRender(offset, consumer, options)
    }

    override fun forceSilentApply() {
        cacheUpToDate = false
    }

    override fun onChildChange(child: Element) {
        super.onChildChange(child)
        forceSilentApply()
    }

    override fun tick() {
        layout.tick()
    }

    companion object : HUDBuilder<LayoutedGUIElement<DebugHUDElement>> {
        override val RESOURCE_LOCATION: ResourceLocation = "minosoft:debug_hud".toResourceLocation()
        override val ENABLE_KEY_BINDING_NAME: ResourceLocation = "minosoft:enable_debug_hud".toResourceLocation()
        override val DEFAULT_ENABLED: Boolean = false
        override val ENABLE_KEY_BINDING: KeyBinding = KeyBinding(
            mapOf(
                KeyAction.STICKY to setOf(KeyCodes.KEY_F3),
            ),
        )

        override fun build(guiRenderer: GUIRenderer): LayoutedGUIElement<DebugHUDElement> {
            return LayoutedGUIElement(DebugHUDElement(guiRenderer)).apply { enabled = false }
        }
    }
}
