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

package de.bixilon.minosoft.gui.rendering.world

import de.bixilon.kotlinglm.vec2.Vec2i
import de.bixilon.kotlinglm.vec3.Vec3
import de.bixilon.kotlinglm.vec3.Vec3i
import de.bixilon.kutil.cast.CastUtil.unsafeCast
import de.bixilon.kutil.collections.CollectionUtil.synchronizedListOf
import de.bixilon.kutil.collections.CollectionUtil.synchronizedSetOf
import de.bixilon.kutil.concurrent.lock.simple.SimpleLock
import de.bixilon.kutil.concurrent.pool.DefaultThreadPool
import de.bixilon.kutil.concurrent.pool.ThreadPool.Priorities.HIGH
import de.bixilon.kutil.concurrent.pool.ThreadPool.Priorities.LOW
import de.bixilon.kutil.concurrent.pool.ThreadPoolRunnable
import de.bixilon.kutil.latch.CountUpAndDownLatch
import de.bixilon.kutil.time.TimeUtil
import de.bixilon.kutil.watcher.DataWatcher.Companion.observe
import de.bixilon.minosoft.config.key.KeyActions
import de.bixilon.minosoft.config.key.KeyBinding
import de.bixilon.minosoft.config.key.KeyCodes
import de.bixilon.minosoft.config.profile.delegate.watcher.SimpleProfileDelegateWatcher.Companion.profileWatch
import de.bixilon.minosoft.data.direction.Directions
import de.bixilon.minosoft.data.registries.ResourceLocation
import de.bixilon.minosoft.data.registries.fluid.FlowableFluid
import de.bixilon.minosoft.data.world.Chunk
import de.bixilon.minosoft.data.world.ChunkSection
import de.bixilon.minosoft.data.world.World
import de.bixilon.minosoft.data.world.view.ViewDistanceChangeEvent
import de.bixilon.minosoft.gui.rendering.RenderWindow
import de.bixilon.minosoft.gui.rendering.RenderingStates
import de.bixilon.minosoft.gui.rendering.modding.events.RenderingStateChangeEvent
import de.bixilon.minosoft.gui.rendering.modding.events.VisibilityGraphChangeEvent
import de.bixilon.minosoft.gui.rendering.renderer.Renderer
import de.bixilon.minosoft.gui.rendering.renderer.RendererBuilder
import de.bixilon.minosoft.gui.rendering.system.base.DepthFunctions
import de.bixilon.minosoft.gui.rendering.system.base.RenderSystem
import de.bixilon.minosoft.gui.rendering.system.base.RenderingCapabilities
import de.bixilon.minosoft.gui.rendering.system.base.phases.OpaqueDrawable
import de.bixilon.minosoft.gui.rendering.system.base.phases.TranslucentDrawable
import de.bixilon.minosoft.gui.rendering.system.base.phases.TransparentDrawable
import de.bixilon.minosoft.gui.rendering.system.base.shader.Shader
import de.bixilon.minosoft.gui.rendering.textures.TextureUtil.texture
import de.bixilon.minosoft.gui.rendering.util.VecUtil.chunkPosition
import de.bixilon.minosoft.gui.rendering.util.VecUtil.empty
import de.bixilon.minosoft.gui.rendering.util.VecUtil.inChunkSectionPosition
import de.bixilon.minosoft.gui.rendering.util.VecUtil.inSectionHeight
import de.bixilon.minosoft.gui.rendering.util.VecUtil.of
import de.bixilon.minosoft.gui.rendering.util.VecUtil.sectionHeight
import de.bixilon.minosoft.gui.rendering.util.vec.vec2.Vec2iUtil.EMPTY
import de.bixilon.minosoft.gui.rendering.util.vec.vec3.Vec3Util.EMPTY
import de.bixilon.minosoft.gui.rendering.util.vec.vec3.Vec3iUtil.EMPTY
import de.bixilon.minosoft.gui.rendering.util.vec.vec3.Vec3iUtil.toVec3
import de.bixilon.minosoft.gui.rendering.world.mesh.VisibleMeshes
import de.bixilon.minosoft.gui.rendering.world.mesh.WorldMesh
import de.bixilon.minosoft.gui.rendering.world.preparer.FluidSectionPreparer
import de.bixilon.minosoft.gui.rendering.world.preparer.SolidSectionPreparer
import de.bixilon.minosoft.gui.rendering.world.preparer.cull.FluidCullSectionPreparer
import de.bixilon.minosoft.gui.rendering.world.preparer.cull.SolidCullSectionPreparer
import de.bixilon.minosoft.modding.event.events.*
import de.bixilon.minosoft.modding.event.invoker.CallbackEventInvoker
import de.bixilon.minosoft.protocol.network.connection.play.PlayConnection
import de.bixilon.minosoft.protocol.network.connection.play.PlayConnectionStates
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition
import de.bixilon.minosoft.util.KUtil.toResourceLocation
import de.bixilon.minosoft.util.chunk.ChunkUtil
import de.bixilon.minosoft.util.chunk.ChunkUtil.loaded
import de.bixilon.minosoft.util.chunk.ChunkUtil.received
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

class WorldRenderer(
    private val connection: PlayConnection,
    override val renderWindow: RenderWindow,
) : Renderer, OpaqueDrawable, TranslucentDrawable, TransparentDrawable {
    private val profile = connection.profiles.block
    override val renderSystem: RenderSystem = renderWindow.renderSystem
    private val shader = renderSystem.createShader("minosoft:world".toResourceLocation())
    private val visibilityGraph = renderWindow.camera.visibilityGraph
    private val transparentShader = renderSystem.createShader("minosoft:world".toResourceLocation())
    private val textShader = renderSystem.createShader("minosoft:world/text".toResourceLocation())
    private val world: World = connection.world
    private val solidSectionPreparer: SolidSectionPreparer = SolidCullSectionPreparer(renderWindow)
    private val fluidSectionPreparer: FluidSectionPreparer = FluidCullSectionPreparer(renderWindow)

    private val loadedMeshes: MutableMap<Vec2i, Int2ObjectOpenHashMap<WorldMesh>> = mutableMapOf() // all prepared (and up to date) meshes
    private val loadedMeshesLock = SimpleLock()

    val maxPreparingTasks = maxOf(DefaultThreadPool.threadCount - 1, 1)
    private val preparingTasks: MutableSet<SectionPrepareTask> = synchronizedSetOf() // current running section preparing tasks
    private val preparingTasksLock = SimpleLock()

    private val queue: MutableList<WorldQueueItem> = synchronizedListOf() // queue, that is visible, and should be rendered
    private val queueLock = SimpleLock()
    private val culledQueue: MutableMap<Vec2i, IntOpenHashSet> = mutableMapOf() // Chunk sections that can be prepared or have changed, but are not required to get rendered yet (i.e. culled chunks)
    private val culledQueueLock = SimpleLock()

    // ToDo: Sometimes if you clear the chunk cache a ton of times, the workers are maxed out and nothing happens anymore
    val maxMeshesToLoad = 100 // ToDo: Should depend on the system memory and other factors.
    private val meshesToLoad: MutableList<WorldQueueItem> = synchronizedListOf() // prepared meshes, that can be loaded in the (next) frame
    private val meshesToLoadLock = SimpleLock()
    private val meshesToUnload: MutableList<WorldMesh> = synchronizedListOf() // prepared meshes, that can be loaded in the (next) frame
    private val meshesToUnloadLock = SimpleLock()

    // all meshes that will be rendered in the next frame (might be changed, when the frustum changes or a chunk gets loaded, ...)
    private var clearVisibleNextFrame = false
    private var visible = VisibleMeshes() // This name might be confusing. Those faces are from blocks.

    private var previousViewDistance = connection.world.view.viewDistance

    private var cameraPosition = Vec3.EMPTY
    private var cameraChunkPosition = Vec2i.EMPTY

    val visibleSize: String
        get() = visible.sizeString
    val loadedMeshesSize: Int by loadedMeshes::size
    val culledQueuedSize: Int by culledQueue::size
    val meshesToLoadSize: Int by meshesToLoad::size
    val queueSize: Int by queue::size
    val preparingTasksSize: Int by preparingTasks::size

    override fun init(latch: CountUpAndDownLatch) {
        renderWindow.modelLoader.load(latch)

        for (fluid in connection.registries.fluidRegistry) {
            if (fluid is FlowableFluid) {
                fluid.flowingTexture = renderWindow.textureManager.staticTextures.createTexture(fluid.flowingTextureName!!.texture())
            }
            fluid.stillTexture = fluid.stillTextureName?.let { texture -> renderWindow.textureManager.staticTextures.createTexture(texture.texture()) }
        }
    }

    private fun loadWorldShader(shader: Shader, animations: Boolean = true) {
        shader.load()
        renderWindow.textureManager.staticTextures.use(shader)
        if (animations) {
            renderWindow.textureManager.staticTextures.animator.use(shader)
        }
        renderWindow.lightMap.use(shader)
    }

    override fun postInit(latch: CountUpAndDownLatch) {
        loadWorldShader(this.shader)

        transparentShader.defines["TRANSPARENT"] = ""
        loadWorldShader(this.transparentShader)

        loadWorldShader(this.textShader, false)


        connection.registerEvent(CallbackEventInvoker.of<VisibilityGraphChangeEvent> { onFrustumChange() })

        connection.registerEvent(CallbackEventInvoker.of<RespawnEvent> { unloadWorld() })
        connection.registerEvent(CallbackEventInvoker.of<ChunkDataChangeEvent> { queueChunk(it.chunkPosition, it.chunk) })
        connection.registerEvent(CallbackEventInvoker.of<BlockSetEvent> {
            val chunkPosition = it.blockPosition.chunkPosition
            val sectionHeight = it.blockPosition.sectionHeight
            val chunk = world[chunkPosition] ?: return@of
            queueSection(chunkPosition, sectionHeight, chunk)
            val inChunkSectionPosition = it.blockPosition.inChunkSectionPosition

            if (inChunkSectionPosition.y == 0) {
                queueSection(chunkPosition, sectionHeight - 1, chunk)
            } else if (inChunkSectionPosition.y == ProtocolDefinition.SECTION_MAX_Y) {
                queueSection(chunkPosition, sectionHeight + 1, chunk)
            }
            if (inChunkSectionPosition.z == 0) {
                queueSection(Vec2i(chunkPosition.x, chunkPosition.y - 1), sectionHeight)
            } else if (inChunkSectionPosition.z == ProtocolDefinition.SECTION_MAX_Z) {
                queueSection(Vec2i(chunkPosition.x, chunkPosition.y + 1), sectionHeight)
            }
            if (inChunkSectionPosition.x == 0) {
                queueSection(Vec2i(chunkPosition.x - 1, chunkPosition.y), sectionHeight)
            } else if (inChunkSectionPosition.x == ProtocolDefinition.SECTION_MAX_X) {
                queueSection(Vec2i(chunkPosition.x + 1, chunkPosition.y), sectionHeight)
            }
        })

        connection.registerEvent(CallbackEventInvoker.of<BlocksSetEvent> {
            val chunk = world[it.chunkPosition] ?: return@of // should not happen
            if (!chunk.isFullyLoaded) {
                return@of
            }
            val sectionHeights: Int2ObjectOpenHashMap<BooleanArray> = Int2ObjectOpenHashMap()
            for (blockPosition in it.blocks.keys) {
                val neighbours = sectionHeights.getOrPut(blockPosition.sectionHeight) { BooleanArray(Directions.SIZE) }
                val inSectionHeight = blockPosition.y.inSectionHeight
                if (inSectionHeight == 0) {
                    neighbours[0] = true
                } else if (inSectionHeight == ProtocolDefinition.SECTION_MAX_Y) {
                    neighbours[1] = true
                }
                if (blockPosition.z == 0) {
                    neighbours[2] = true
                } else if (blockPosition.z == ProtocolDefinition.SECTION_MAX_Z) {
                    neighbours[3] = true
                }
                if (blockPosition.x == 0) {
                    neighbours[4] = true
                } else if (blockPosition.x == ProtocolDefinition.SECTION_MAX_X) {
                    neighbours[5] = true
                }
            }
            for ((sectionHeight, neighbourUpdates) in sectionHeights) {
                queueSection(it.chunkPosition, sectionHeight, chunk)

                if (neighbourUpdates[0]) {
                    queueSection(it.chunkPosition, sectionHeight - 1, chunk)
                }
                if (neighbourUpdates[1]) {
                    queueSection(it.chunkPosition, sectionHeight + 1, chunk)
                }
                if (neighbourUpdates[2]) {
                    queueSection(Vec2i(it.chunkPosition.x, it.chunkPosition.y - 1), sectionHeight)
                }
                if (neighbourUpdates[3]) {
                    queueSection(Vec2i(it.chunkPosition.x, it.chunkPosition.y + 1), sectionHeight)
                }
                if (neighbourUpdates[4]) {
                    queueSection(Vec2i(it.chunkPosition.x - 1, it.chunkPosition.y), sectionHeight)
                }
                if (neighbourUpdates[5]) {
                    queueSection(Vec2i(it.chunkPosition.x + 1, it.chunkPosition.y), sectionHeight)
                }
            }
        })

        connection.registerEvent(CallbackEventInvoker.of<ChunkUnloadEvent> { unloadChunk(it.chunkPosition) })
        connection::state.observe(this) { if (it == PlayConnectionStates.DISCONNECTED) unloadWorld() }
        connection.registerEvent(CallbackEventInvoker.of<RenderingStateChangeEvent> {
            if (it.state == RenderingStates.PAUSED) {
                unloadWorld()
            } else if (it.previousState == RenderingStates.PAUSED) {
                prepareWorld()
            }
        })
        connection.registerEvent(CallbackEventInvoker.of<BlockDataChangeEvent> { queueSection(it.blockPosition.chunkPosition, it.blockPosition.sectionHeight) })

        renderWindow.inputHandler.registerKeyCallback("minosoft:clear_chunk_cache".toResourceLocation(),
            KeyBinding(
                mapOf(
                    KeyActions.MODIFIER to setOf(KeyCodes.KEY_F3),
                    KeyActions.PRESS to setOf(KeyCodes.KEY_A),
                ),
            )) { clearChunkCache() }

        profile.rendering::antiMoirePattern.profileWatch(this, false, profile) { clearChunkCache() }
        val rendering = connection.profiles.rendering
        rendering.performance::fastBedrock.profileWatch(this, false, rendering) { clearChunkCache() }

        connection.registerEvent(CallbackEventInvoker.of<ViewDistanceChangeEvent> { event ->
            if (event.viewDistance < this.previousViewDistance) {
                // Unload all chunks(-sections) that are out of view distance
                queueLock.lock()
                culledQueueLock.lock()
                meshesToLoadLock.lock()
                meshesToUnloadLock.lock()
                loadedMeshesLock.lock()

                val loadedMeshesToRemove: MutableSet<Vec2i> = HashSet()
                for ((chunkPosition, sections) in loadedMeshes) {
                    if (visibilityGraph.isChunkVisible(chunkPosition)) {
                        continue
                    }
                    loadedMeshesToRemove += chunkPosition
                    for (mesh in sections.values) {
                        if (mesh in meshesToUnload) {
                            continue
                        }
                        meshesToUnload += mesh
                    }
                }
                loadedMeshes -= loadedMeshesToRemove

                val toRemove: MutableSet<Vec2i> = HashSet()
                for (chunkPosition in culledQueue.keys) {
                    if (visibilityGraph.isChunkVisible(chunkPosition)) {
                        continue
                    }
                    toRemove += chunkPosition
                }
                culledQueue -= toRemove

                queue.removeAll { !visibilityGraph.isChunkVisible(it.chunkPosition) }

                meshesToLoad.removeAll { !visibilityGraph.isChunkVisible(it.chunkPosition) }

                preparingTasksLock.acquire()
                for (task in preparingTasks.toMutableSet()) {
                    if (!visibilityGraph.isChunkVisible(task.chunkPosition)) {
                        task.runnable.interrupt()
                    }
                }
                preparingTasksLock.release()

                loadedMeshesLock.unlock()
                queueLock.unlock()
                culledQueueLock.unlock()
                meshesToLoadLock.unlock()
                meshesToUnloadLock.unlock()
            } else {
                prepareWorld()
            }

            this.previousViewDistance = event.viewDistance
        })
    }

    private fun clearChunkCache() {
        unloadWorld()
        prepareWorld()
        connection.util.sendDebugMessage("Chunk cache cleared!")
    }

    private fun prepareWorld() {
        world.lock.acquire()
        for ((chunkPosition, chunk) in world.chunks) {
            queueChunk(chunkPosition, chunk)
        }
        world.lock.release()
    }

    private fun unloadWorld() {
        queueLock.lock()
        culledQueueLock.lock()
        meshesToLoadLock.lock()
        loadedMeshesLock.lock()

        meshesToUnloadLock.lock()
        for (sections in loadedMeshes.values) {
            for (mesh in sections.values) {
                meshesToUnload += mesh
            }
        }
        meshesToUnloadLock.unlock()

        culledQueue.clear()
        loadedMeshes.clear()
        queue.clear()
        meshesToLoad.clear()

        clearVisibleNextFrame = true

        preparingTasksLock.acquire()
        for (task in preparingTasks.toMutableSet()) {
            task.runnable.interrupt()
        }
        preparingTasksLock.release()

        loadedMeshesLock.unlock()
        queueLock.unlock()
        culledQueueLock.unlock()
        meshesToLoadLock.unlock()
    }

    private fun unloadChunk(chunkPosition: Vec2i) {
        queueLock.lock()
        culledQueueLock.lock()
        meshesToLoadLock.lock()
        meshesToUnloadLock.lock()
        loadedMeshesLock.lock()
        val meshes = loadedMeshes.remove(chunkPosition)

        culledQueue.remove(chunkPosition)

        queue.removeAll { it.chunkPosition == chunkPosition }

        meshesToLoad.removeAll { it.chunkPosition == chunkPosition }

        preparingTasksLock.acquire()
        for (task in preparingTasks.toMutableSet()) {
            if (task.chunkPosition == chunkPosition) {
                task.runnable.interrupt()
            }
        }
        preparingTasksLock.release()
        if (meshes != null) {
            for (mesh in meshes.values) {
                meshesToUnload += mesh
            }
            loadedMeshes -= chunkPosition
        }

        loadedMeshesLock.unlock()
        queueLock.unlock()
        culledQueueLock.unlock()
        meshesToLoadLock.unlock()
        meshesToUnloadLock.unlock()
    }

    private fun sortQueue() {
        // ToDo: This dirty sorts the queue without locking. Check for crashes...
        //  queueLock.lock()
        queue.sortBy { (it.center - cameraPosition).length2() }
        // queueLock.unlock()
    }

    private fun workQueue() {
        // ToDo: Prepare some of the culledChunks if nothing todo
        val size = preparingTasks.size
        if (size >= maxPreparingTasks && queue.isNotEmpty() || meshesToLoad.size >= maxMeshesToLoad) {
            return
        }
        val items: MutableList<WorldQueueItem> = mutableListOf()
        queueLock.lock()
        for (i in 0 until maxPreparingTasks - size) {
            if (queue.size == 0) {
                break
            }
            items += queue.removeAt(0)
        }
        queueLock.unlock()
        for (item in items) {
            val task = SectionPrepareTask(item.chunkPosition, item.sectionHeight, ThreadPoolRunnable(if (item.chunkPosition == cameraChunkPosition) HIGH else LOW)) // Our own chunk is the most important one ToDo: Also make neighbour chunks important
            task.runnable.runnable = Runnable {
                var locked = false
                try {
                    val chunk = item.chunk ?: world[item.chunkPosition] ?: return@Runnable
                    val section = chunk[item.sectionHeight] ?: return@Runnable
                    if (section.blocks.isEmpty) {
                        return@Runnable queueItemUnload(item)
                    }
                    val neighbourChunks: Array<Chunk>
                    world.getChunkNeighbours(item.chunkPosition).let {
                        if (!it.loaded) {
                            return@Runnable queueSection(item.chunkPosition, item.sectionHeight, chunk, section)
                        }
                        neighbourChunks = it.unsafeCast()
                    }
                    val neighbours = item.neighbours ?: ChunkUtil.getDirectNeighbours(neighbourChunks, chunk, item.sectionHeight)
                    val mesh = WorldMesh(renderWindow, item.chunkPosition, item.sectionHeight, section.blocks.count < ProtocolDefinition.SECTION_MAX_X * ProtocolDefinition.SECTION_MAX_Z)
                    solidSectionPreparer.prepareSolid(item.chunkPosition, item.sectionHeight, chunk, section, neighbours, neighbourChunks, mesh)
                    if (section.blocks.fluidCount > 0) {
                        fluidSectionPreparer.prepareFluid(item.chunkPosition, item.sectionHeight, chunk, section, neighbours, neighbourChunks, mesh)
                    }
                    if (mesh.clearEmpty() == 0) {
                        return@Runnable queueItemUnload(item)
                    }
                    item.mesh = mesh
                    meshesToLoadLock.lock()
                    locked = true
                    meshesToLoad.removeIf { it == item } // Remove duplicates
                    if (item.chunkPosition == cameraChunkPosition) {
                        // still higher priority
                        meshesToLoad.add(0, item)
                    } else {
                        meshesToLoad += item
                    }
                    meshesToLoadLock.unlock()
                } catch (exception: Throwable) {
                    if (locked) {
                        meshesToLoadLock.unlock()
                    }
                    if (exception !is InterruptedException) {
                        // otherwise task got interrupted (probably because of chunk unload)
                        throw exception
                    }
                } finally {
                    preparingTasksLock.lock()
                    preparingTasks -= task
                    preparingTasksLock.unlock()
                    workQueue()
                }
            }
            preparingTasksLock.lock()
            preparingTasks += task
            preparingTasksLock.unlock()
            DefaultThreadPool += task.runnable
        }
    }

    private fun queueItemUnload(item: WorldQueueItem) {
        queueLock.lock()
        culledQueueLock.lock()
        meshesToLoadLock.lock()
        meshesToUnloadLock.lock()
        loadedMeshesLock.lock()
        loadedMeshes[item.chunkPosition]?.let {
            meshesToUnload += it.remove(item.sectionHeight) ?: return@let
            if (it.isEmpty()) {
                loadedMeshes -= item.chunkPosition
            }
        }

        culledQueue[item.chunkPosition]?.let {
            it.remove(item.sectionHeight)
            if (it.isEmpty()) {
                culledQueue -= item.chunkPosition
            }
        }

        queue.removeAll { it.chunkPosition == item.chunkPosition && it.sectionHeight == item.sectionHeight }

        meshesToLoad.removeAll { it.chunkPosition == item.chunkPosition && it.sectionHeight == item.sectionHeight }

        preparingTasksLock.acquire()
        for (task in preparingTasks.toMutableSet()) {
            if (task.chunkPosition == item.chunkPosition && task.sectionHeight == item.sectionHeight) {
                task.runnable.interrupt()
            }
        }
        preparingTasksLock.release()

        loadedMeshesLock.unlock()
        queueLock.unlock()
        culledQueueLock.unlock()
        meshesToLoadLock.unlock()
        meshesToUnloadLock.unlock()
    }

    private fun internalQueueSection(chunkPosition: Vec2i, sectionHeight: Int, chunk: Chunk, section: ChunkSection, ignoreFrustum: Boolean): Boolean {
        if (!chunk.isFullyLoaded) { // ToDo: Unload if empty
            return false
        }
        val item = WorldQueueItem(chunkPosition, sectionHeight, chunk, section, Vec3i.of(chunkPosition, sectionHeight).toVec3() + CHUNK_CENTER, null)
        if (section.blocks.isEmpty) {
            queueItemUnload(item)
            return false
        }

        val neighbours = world.getChunkNeighbours(chunkPosition)
        if (!neighbours.received) {
            return false
        }

        val visible = ignoreFrustum || visibilityGraph.isSectionVisible(chunkPosition, sectionHeight, section.blocks.minPosition, section.blocks.maxPosition, true)
        if (visible) {
            item.neighbours = ChunkUtil.getDirectNeighbours(neighbours.unsafeCast(), chunk, sectionHeight)
            queueLock.lock()
            queue.removeIf { it == item } // Prevent duplicated entries (to not prepare the same chunk twice (if it changed and was not prepared yet or ...)
            if (chunkPosition == cameraChunkPosition) {
                queue.add(0, item)
            } else {
                queue += item
            }
            queueLock.unlock()
            return true
        } else {
            culledQueueLock.lock()
            culledQueue.getOrPut(chunkPosition) { IntOpenHashSet() } += sectionHeight
            culledQueueLock.unlock()
        }
        return false
    }

    private fun queueSection(chunkPosition: Vec2i, sectionHeight: Int, chunk: Chunk? = world.chunks[chunkPosition], section: ChunkSection? = chunk?.get(sectionHeight), ignoreFrustum: Boolean = false) {
        if (chunk == null || section == null || renderWindow.renderingState == RenderingStates.PAUSED) {
            return
        }
        val queued = internalQueueSection(chunkPosition, sectionHeight, chunk, section, ignoreFrustum)

        if (queued) {
            sortQueue()
            workQueue()
        }
    }

    private fun queueChunk(chunkPosition: Vec2i, chunk: Chunk = world.chunks[chunkPosition]!!) {
        if (!chunk.isFullyLoaded || renderWindow.renderingState == RenderingStates.PAUSED) {
            return
        }
        if (this.loadedMeshes.containsKey(chunkPosition)) {
            // ToDo: this also ignores light updates
            return
        }

        // ToDo: Check if chunk is visible (not section, chunk)
        var queueChanges = 0
        for (sectionHeight in chunk.lowestSection until chunk.highestSection) {
            val section = chunk[sectionHeight] ?: continue
            val queued = internalQueueSection(chunkPosition, sectionHeight, chunk, section, false)
            if (queued) {
                queueChanges++
            }
        }
        if (queueChanges > 0) {
            sortQueue()
            workQueue()
        }
    }

    private fun loadMeshes() {
        meshesToLoadLock.acquire()
        if (meshesToLoad.isEmpty()) {
            meshesToLoadLock.release()
            return
        }

        var addedMeshes = 0
        val time = TimeUtil.millis
        val maxTime = if (connection.player.velocity.empty) 50L else 20L // If the player is still, then we can load more chunks (to not cause lags)

        while ((TimeUtil.millis - time < maxTime) && meshesToLoad.isNotEmpty()) {
            val item = meshesToLoad.removeAt(0)
            val mesh = item.mesh ?: continue

            mesh.load()

            loadedMeshesLock.lock()
            val meshes = loadedMeshes.getOrPut(item.chunkPosition) { Int2ObjectOpenHashMap() }

            meshes.put(item.sectionHeight, mesh)?.let {
                this.visible.removeMesh(it)
                it.unload()
            }
            loadedMeshesLock.unlock()

            val visible = visibilityGraph.isSectionVisible(item.chunkPosition, item.sectionHeight, mesh.minPosition, mesh.maxPosition, true)
            if (visible) {
                addedMeshes++
                this.visible.addMesh(mesh)
            }
        }
        meshesToLoadLock.release()

        if (addedMeshes > 0) {
            visible.sort()
        }
    }

    private fun unloadMeshes() {
        meshesToUnloadLock.acquire()
        if (meshesToUnload.isEmpty()) {
            meshesToUnloadLock.release()
            return
        }

        val time = TimeUtil.millis
        val maxTime = if (connection.player.velocity.empty) 50L else 20L // If the player is still, then we can load more chunks (to not cause lags)

        while ((TimeUtil.millis - time < maxTime) && meshesToUnload.isNotEmpty()) {
            val mesh = meshesToUnload.removeAt(0)
            visible.removeMesh(mesh)
            mesh.unload()
        }
        meshesToUnloadLock.release()
    }

    override fun prepareDraw() {
        renderWindow.textureManager.staticTextures.use(shader)
        if (clearVisibleNextFrame) {
            visible.clear()
            clearVisibleNextFrame = false
        }
        unloadMeshes()
        loadMeshes()
    }

    override fun setupOpaque() {
        super.setupOpaque()
        shader.use()
    }

    override fun drawOpaque() {
        for (mesh in visible.opaque) {
            mesh.draw()
        }

        renderWindow.renderSystem.depth = DepthFunctions.LESS_OR_EQUAL
        for (blockEntity in visible.blockEntities) {
            blockEntity.draw(renderWindow)
        }
    }

    override fun setupTranslucent() {
        super.setupTranslucent()
        shader.use()
    }

    override fun drawTranslucent() {
        for (mesh in visible.translucent) {
            mesh.draw()
        }
    }

    override fun setupTransparent() {
        super.setupTransparent()
        transparentShader.use()
    }

    override fun drawTransparent() {
        for (mesh in visible.transparent) {
            mesh.draw()
        }

        renderWindow.renderSystem.depth = DepthFunctions.LESS_OR_EQUAL
        renderWindow.renderSystem[RenderingCapabilities.POLYGON_OFFSET] = true
        renderWindow.renderSystem.polygonOffset(-2.5f, -2.5f)
        textShader.use()
        for (mesh in visible.text) {
            mesh.draw()
        }
    }

    private fun onFrustumChange() {
        var sortQueue = false
        val cameraPosition = connection.player.cameraPosition
        if (this.cameraPosition != cameraPosition) {
            this.cameraPosition = cameraPosition
            this.cameraChunkPosition = connection.player.positionInfo.chunkPosition
            sortQueue = true
        }

        val visible = VisibleMeshes(cameraPosition)

        loadedMeshesLock.acquire()
        for ((chunkPosition, meshes) in this.loadedMeshes) {
            if (!visibilityGraph.isChunkVisible(chunkPosition)) {
                continue
            }

            for ((sectionHeight, mesh) in meshes) {
                if (visibilityGraph.isSectionVisible(chunkPosition, sectionHeight, mesh.minPosition, mesh.maxPosition, false)) {
                    visible.addMesh(mesh)
                }
            }
        }
        loadedMeshesLock.release()

        culledQueueLock.acquire()
        val queue: MutableMap<Vec2i, IntOpenHashSet> = mutableMapOf() // The queue method needs the full lock of the culledQueue
        for ((chunkPosition, sectionHeights) in this.culledQueue) {
            if (!visibilityGraph.isChunkVisible(chunkPosition)) {
                continue
            }
            var chunkQueue: IntOpenHashSet? = null
            for (sectionHeight in sectionHeights.intIterator()) {
                if (!visibilityGraph.isSectionVisible(chunkPosition, sectionHeight, Vec3i.EMPTY, CHUNK_SIZE, false)) {
                    continue
                }
                if (chunkQueue == null) {
                    chunkQueue = queue.getOrPut(chunkPosition) { IntOpenHashSet() }
                }
                chunkQueue += sectionHeight
            }
        }

        culledQueueLock.release()


        for ((chunkPosition, sectionHeights) in queue) {
            for (sectionHeight in sectionHeights.intIterator()) {
                queueSection(chunkPosition, sectionHeight, ignoreFrustum = true)
            }
        }
        if (queue.isNotEmpty()) {
            sortQueue()
            workQueue()
            sortQueue = false
        }

        culledQueueLock.acquire()
        for ((chunkPosition, sectionHeights) in queue) {
            val originalSectionHeight = this.culledQueue[chunkPosition] ?: continue
            originalSectionHeight -= sectionHeights
            if (originalSectionHeight.isEmpty()) {
                this.culledQueue -= chunkPosition
            }
        }
        culledQueueLock.release()

        visible.sort()

        this.visible = visible


        if (sortQueue) {
            sortQueue()
        }
    }


    companion object : RendererBuilder<WorldRenderer> {
        override val RESOURCE_LOCATION = ResourceLocation("minosoft:world")
        private val CHUNK_SIZE = Vec3i(ProtocolDefinition.SECTION_MAX_X, ProtocolDefinition.SECTION_MAX_Y, ProtocolDefinition.SECTION_MAX_Z)
        private val CHUNK_CENTER = Vec3(CHUNK_SIZE) / 2.0f

        override fun build(connection: PlayConnection, renderWindow: RenderWindow): WorldRenderer {
            return WorldRenderer(connection, renderWindow)
        }
    }
}
