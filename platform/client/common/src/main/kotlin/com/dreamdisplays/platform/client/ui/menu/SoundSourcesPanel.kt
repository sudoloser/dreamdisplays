package com.dreamdisplays.platform.client.ui.menu

import com.dreamdisplays.core.protocol.BindSpeaker
import com.dreamdisplays.core.protocol.SetRoomConfined
import com.dreamdisplays.core.protocol.SpeakerInfo
import com.dreamdisplays.platform.client.Initializer
import com.dreamdisplays.platform.client.displays.DisplayScreen
import com.dreamdisplays.platform.client.displays.SpeakerRegistry
import com.dreamdisplays.platform.client.ui.GuiGraphicsCompat
import com.dreamdisplays.platform.client.ui.drawText
import com.dreamdisplays.platform.client.ui.kit.*
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import java.util.UUID

/**
 * The "sound sources" popup opened by the speakers button in the display menu: lists the display's
 * bound speakers (each with an "×" to unbind), an "add speaker" row that opens [addDropdown] (pick a
 * registered speaker to bind it), and a "confine to room" toggle that hard-mutes the display for
 * listeners outside its speakers' rooms. Owns its hit-testing like [PopoutDropdown]; the menu only
 * toggles it, renders it, and forwards clicks.
 *
 * All changes are applied optimistically to [display] and mirrored to the server (which rebroadcasts
 * the authoritative state).
 */
class SoundSourcesPanel(
    private val display: DisplayScreen,
) {
    private var visible = false
    private var rect = UiRect(0, 0, WIDTH, HEADER_H)
    private var addRect = UiRect(0, 0, WIDTH, ADD_H)
    private var toggleRect = UiRect(0, 0, WIDTH, TOGGLE_H)
    private val removeRects = mutableListOf<Pair<UUID, UiRect>>()
    private var animProgress = 0f
    private var lastFrameNanos = 0L

    /** Dropdown of still-bindable speakers, opened by the "add speaker" row. */
    val addDropdown = SpeakerDropdown(
        getAvailable = { SpeakerRegistry.all().filter { it.id !in display.speakerIds } },
        onSpeaker = ::bind,
    )

    fun isVisible(): Boolean = visible

    /** Toggles the panel; hides the nested add-dropdown when closing. */
    fun toggle() {
        visible = !visible
        if (!visible) addDropdown.hide()
        lastFrameNanos = 0L
    }

    fun hide() {
        visible = false
        addDropdown.hide()
    }

    private fun bind(id: UUID) {
        if (id in display.speakerIds) return
        if (display.speakerIds.size >= MAX_SPEAKERS) return
        display.speakerIds = display.speakerIds + id
        Initializer.sendPacket(BindSpeaker(display.uuid, id, true))
    }

    private fun unbind(id: UUID) {
        display.speakerIds = display.speakerIds.filter { it != id }
        Initializer.sendPacket(BindSpeaker(display.uuid, id, false))
    }

    private fun setRoomConfined(value: Boolean) {
        display.roomConfined = value
        Initializer.sendPacket(SetRoomConfined(display.uuid, value))
    }

    private fun boundSpeakers(): List<SpeakerInfo> =
        display.speakerIds.mapNotNull { SpeakerRegistry.get(it) }

    /** Draws the panel opening upward from ([anchorCenterX], [anchorTopY]) — the speaker button. */
    fun draw(g: GuiGraphicsCompat, anchorCenterX: Int, anchorTopY: Int, mouseX: Int, mouseY: Int) {
        if (!visible) return
        val bound = boundSpeakers()
        val height = HEADER_H + ADD_H + TOGGLE_H + SPEAKER_H * bound.size
        rect = UiRect(anchorCenterX - WIDTH / 2, anchorTopY - height - 2, WIDTH, height)

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.016f else ((now - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameNanos = now
        animProgress += (1f - animProgress) * minOf(1f, dt * 12f)
        if (animProgress < 0.01f) animProgress = 0f

        g.drawPanelSprite(rect, PANEL_SPRITE, animProgress)

        val font = Minecraft.getInstance().font
        g.drawText(
            font,
            Component.translatable("dreamdisplays.ui.speakers.title"),
            rect.x + 6,
            rect.y + 4,
            scaleAlpha(UiTheme.TEXT_PRIMARY, animProgress),
            false,
        )

        removeRects.clear()
        var y = rect.y + HEADER_H
        bound.forEach { s ->
            val rowY = y
            g.fill(rect.x + 2, rowY, rect.x + WIDTH - 2, rowY + SPEAKER_H, scaleAlpha(UiTheme.ROW_BG, animProgress))
            val label = UiText.trim(font, s.name + "  [" + s.x + ", " + s.y + ", " + s.z + "]", WIDTH - 6 - 6 - 16 - 6)
            g.drawText(
                font,
                label,
                rect.x + 6,
                rowY + (SPEAKER_H - font.lineHeight) / 2,
                scaleAlpha(UiTheme.TEXT_SECONDARY, animProgress),
                false,
            )
            val btn = UiRect(rect.x + WIDTH - 6 - 16, rowY + (SPEAKER_H - 16) / 2, 16, 16)
            if (btn.contains(mouseX, mouseY)) {
                g.fill(btn.x, btn.y, btn.right, btn.bottom, scaleAlpha(UiTheme.HOVER_FILL, animProgress))
            }
            g.drawText(font, "×", btn.x + 4, btn.y + (16 - font.lineHeight) / 2, scaleAlpha(UiTheme.TEXT_PRIMARY, animProgress), false)
            removeRects.add(s.id to btn)
            y += SPEAKER_H
        }

        val hoveringAdd = addRect.contains(mouseX, mouseY)
        if (hoveringAdd) {
            g.fill(rect.x + 2, y, rect.x + WIDTH - 2, y + ADD_H, scaleAlpha(UiTheme.HOVER_FILL, animProgress))
        }
        g.drawText(
            font,
            Component.translatable("dreamdisplays.ui.speakers.add"),
            rect.x + 6,
            y + (ADD_H - font.lineHeight) / 2,
            scaleAlpha(if (hoveringAdd) UiTheme.TEXT_PRIMARY else UiTheme.TEXT_SECONDARY, animProgress),
            false,
        )
        addRect = UiRect(rect.x, y, WIDTH, ADD_H)
        y += ADD_H

        val hoveringToggle = toggleRect.contains(mouseX, mouseY)
        if (hoveringToggle) {
            g.fill(rect.x + 2, y, rect.x + WIDTH - 2, y + TOGGLE_H, scaleAlpha(UiTheme.HOVER_FILL, animProgress))
        }
        g.drawText(
            font,
            Component.translatable("dreamdisplays.ui.speakers.confine"),
            rect.x + 6,
            y + (TOGGLE_H - font.lineHeight) / 2,
            scaleAlpha(UiTheme.TEXT_SECONDARY, animProgress),
            false,
        )
        val stateKey = if (display.roomConfined) "dreamdisplays.ui.speakers.on" else "dreamdisplays.ui.speakers.off"
        val stateColor = if (display.roomConfined) UiTheme.ACCENT else UiTheme.TEXT_DIM
        g.drawText(
            font,
            Component.translatable(stateKey),
            rect.x + WIDTH - 6 - font.width(Component.translatable(stateKey)),
            y + (TOGGLE_H - font.lineHeight) / 2,
            scaleAlpha(stateColor, animProgress),
            false,
        )
        toggleRect = UiRect(rect.x, y, WIDTH, TOGGLE_H)

        if (addDropdown.isVisible()) {
            addDropdown.draw(g, addRect.x + 6, addRect.bottom, mouseX, mouseY)
        }
    }

    /** Handles a left click while the panel is visible; returns true when consumed. */
    fun handleClick(mx: Int, my: Int): Boolean {
        if (!visible) return false
        if (addRect.contains(mx, my)) {
            sound()
            if (addDropdown.isVisible()) addDropdown.hide()
            else addDropdown.show(addRect.x + 6, addRect.bottom)
            return true
        }
        if (addDropdown.isVisible()) {
            if (addDropdown.handleClick(mx, my)) {
                sound()
                return true
            }
            if (addDropdown.isVisible()) return true
        }
        removeRects.firstOrNull { (_, r) -> r.contains(mx, my) }?.let { (id, _) ->
            sound()
            unbind(id)
            return true
        }
        if (toggleRect.contains(mx, my)) {
            sound()
            setRoomConfined(!display.roomConfined)
            return true
        }
        if (rect.contains(mx, my)) return true
        visible = false
        return false
    }

    private fun sound() {
        val s = SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f)
        Minecraft.getInstance().soundManager.play(s)
    }

    private companion object {
        const val WIDTH = 240
        const val HEADER_H = 22
        const val SPEAKER_H = 20
        const val ADD_H = 20
        const val TOGGLE_H = 22

        /** Matches the server-side cap; keep in sync with `SpeakerManager.MAX_SPEAKERS_PER_DISPLAY`. */
        const val MAX_SPEAKERS = 10
    }
}
