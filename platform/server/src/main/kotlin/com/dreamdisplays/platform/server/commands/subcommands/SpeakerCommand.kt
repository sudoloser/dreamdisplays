package com.dreamdisplays.platform.server.commands.subcommands

import com.dreamdisplays.platform.server.PaperServer
import com.dreamdisplays.platform.server.VanillaServerState
import com.dreamdisplays.platform.server.datatypes.display.PaperDisplayData
import com.dreamdisplays.platform.server.datatypes.display.SpeakerData
import com.dreamdisplays.platform.server.datatypes.display.VanillaDisplayData
import com.dreamdisplays.platform.server.managers.DisplayManager
import com.dreamdisplays.platform.server.managers.SpeakerManager
import com.dreamdisplays.platform.server.meta.ServerCoroutines
import com.dreamdisplays.platform.server.utils.MessageUtil
import com.dreamdisplays.platform.server.utils.RegionUtil
import com.dreamdisplays.platform.server.utils.VanillaPermissions
import com.dreamdisplays.platform.server.utils.net.DisplayActions
import com.dreamdisplays.platform.server.utils.net.VanillaPacketUtil
import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.launch
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

/**
 * Handles `/display speaker create|list|delete`. Speakers are named sound-source points that
 * displays bind (up to [SpeakerManager.MAX_SPEAKERS_PER_DISPLAY] per display) to route audio
 * through and to confine audio to their room.
 */
object PaperSpeakerCommand {
    /** Handles `/display speaker create [name] [radius]`, placing the speaker at [player]'s position. */
    fun create(sender: CommandSender, name: String?, radius: Double?) {
        val player = sender as? Player ?: return
        if (!player.hasPermission(PaperServer.config.permissions.speaker)) {
            MessageUtil.sendMessage(player, "displayCommandMissingPermission")
            return
        }
        val pos = player.location
        val speaker = SpeakerData(
            id = UUID.randomUUID(),
            name = name?.takeIf { it.isNotBlank() } ?: "speaker-${pos.blockX}-${pos.blockY}-${pos.blockZ}",
            world = player.world.name,
            x = pos.blockX,
            y = pos.blockY,
            z = pos.blockZ,
            radius = radius?.toFloat()?.coerceIn(1f, 256f) ?: 16f,
        )
        SpeakerManager.register(speaker)
        sendSpeakers(Bukkit.getOnlinePlayers().toList())
        MessageUtil.sendMessage(player, "speakerCreated", speaker.name, speaker.id.toString())
    }

    /** Handles `/display speaker list`. */
    fun list(sender: CommandSender) {
        val speakers = SpeakerManager.list()
        if (speakers.isEmpty()) return MessageUtil.sendMessage(sender, "speakerListEmpty")
        speakers.forEach { s ->
            MessageUtil.sendColoredMessage(
                sender,
                MessageUtil.formatIndexed(
                    sender, "speakerListEntry",
                    s.id.toString().substring(0, 8), s.name, s.world,
                    s.x.toString(), s.y.toString(), s.z.toString(),
                ),
            )
        }
    }

    /** Handles `/display speaker delete <id|name>`, also unbinding it from every display. */
    fun delete(sender: CommandSender, token: String) {
        if (!sender.hasPermission(PaperServer.config.permissions.speaker)) {
            MessageUtil.sendMessage(sender, "displayCommandMissingPermission")
            return
        }
        val speaker = SpeakerManager.resolve(token)
            ?: return MessageUtil.sendMessage(sender, "speakerNotFound")
        SpeakerManager.delete(speaker.id)
        unbindFromAllDisplays(speaker.id)
        sendSpeakers(Bukkit.getOnlinePlayers().toList())
        MessageUtil.sendMessage(sender, "speakerDeleted", speaker.name)
    }

    /** Removes [speakerId] from every display binding it and broadcasts the updated displays. */
    private fun unbindFromAllDisplays(speakerId: UUID) {
        DisplayManager.getDisplays().filterIsInstance<PaperDisplayData>()
            .filter { speakerId in it.speakers }
            .forEach { data ->
                data.speakers = data.speakers.filter { it != speakerId }
                DisplayActions.persistAndBroadcast(data)
            }
    }

    /** Pushes the updated speaker registry to [players] (filtered per world on send). */
    private fun sendSpeakers(players: List<Player>) {
        if (players.isNotEmpty()) DisplayActions.sendSpeakers(players)
    }
}

/** Shared `Fabric` / `NeoForge` adapter for `/display speaker create|list|delete`. */
object VanillaSpeakerCommand {
    private fun allowed(player: ServerPlayer, node: String): Boolean =
        VanillaPermissions.has(player, node, VanillaPermissions.Fallback.OP)

    /** Handles `/display speaker create [name] [radius]`, placing the speaker at [player]'s position. */
    fun create(ctx: CommandContext<CommandSourceStack>, name: String?, radius: Double?): Int {
        val player = ctx.source.player ?: return 0
        if (!allowed(player, VanillaServerState.config.permissions.speaker)) {
            MessageUtil.sendMessage(player, "displayCommandMissingPermission")
            return 0
        }
        val speaker = SpeakerData(
            id = UUID.randomUUID(),
            name = name?.takeIf { it.isNotBlank() } ?: "speaker-${player.blockPosition().x}-${player.blockPosition().y}-${player.blockPosition().z}",
            world = RegionUtil.getPlayerLevelKey(player),
            x = player.blockPosition().x,
            y = player.blockPosition().y,
            z = player.blockPosition().z,
            radius = radius?.toFloat()?.coerceIn(1f, 256f) ?: 16f,
        )
        SpeakerManager.register(speaker)
        sendSpeakers(ctx)
        MessageUtil.sendMessage(player, "speakerCreated", speaker.name, speaker.id.toString())
        return 1
    }

    /** Handles `/display speaker list`. */
    fun list(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player
        val speakers = SpeakerManager.list()
        if (speakers.isEmpty()) {
            MessageUtil.sendMessage(player, "speakerListEmpty")
            return 1
        }
        speakers.forEach { s ->
            MessageUtil.sendColoredMessage(
                player,
                MessageUtil.formatIndexed(
                    player, "speakerListEntry",
                    s.id.toString().substring(0, 8), s.name, s.world,
                    s.x.toString(), s.y.toString(), s.z.toString(),
                ),
            )
        }
        return 1
    }

    /** Handles `/display speaker delete <id|name>`, also unbinding it from every display. */
    fun delete(ctx: CommandContext<CommandSourceStack>, token: String): Int {
        val player = ctx.source.player ?: return 0
        if (!allowed(player, VanillaServerState.config.permissions.speaker)) {
            MessageUtil.sendMessage(player, "displayCommandMissingPermission")
            return 0
        }
        val speaker = SpeakerManager.resolve(token)
            ?: return MessageUtil.sendMessage(player, "speakerNotFound").let { 0 }
        SpeakerManager.delete(speaker.id)
        unbindFromAllDisplays(ctx, speaker.id)
        sendSpeakers(ctx)
        MessageUtil.sendMessage(player, "speakerDeleted", speaker.name)
        return 1
    }

    /** Removes [speakerId] from every display binding it and broadcasts the updated displays. */
    private fun unbindFromAllDisplays(ctx: CommandContext<CommandSourceStack>, speakerId: UUID) {
        DisplayManager.getDisplays().filterIsInstance<VanillaDisplayData>()
            .filter { speakerId in it.speakers }
            .forEach { data ->
                data.speakers = data.speakers.filter { it != speakerId }
                ServerCoroutines.io.launch { VanillaServerState.storage?.saveDisplay(data) }
                VanillaPacketUtil.sendDisplayInfo(DisplayManager.getReceivers(data, ctx.source.server), data)
            }
    }

    /** Pushes the updated speaker registry to every online player (filtered per world on send). */
    private fun sendSpeakers(ctx: CommandContext<CommandSourceStack>) {
        VanillaPacketUtil.sendSpeakers(ctx.source.server.playerList.players)
    }
}
