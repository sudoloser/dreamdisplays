package com.dreamdisplays.platform.client.ui.menu

import com.dreamdisplays.core.protocol.SpeakerInfo
import com.dreamdisplays.platform.client.ui.GuiGraphicsCompat
import com.dreamdisplays.platform.client.ui.drawText
import com.dreamdisplays.platform.client.ui.kit.*
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import java.util.UUID

/**
 * Downward dropdown listing the speakers a display can still bind (registered but not already bound
 * to this display). Selecting one fires [onSpeaker] with its id, then hides. When nothing is
 * available a single disabled "no more speakers" row is drawn instead. Modeled on [PopoutDropdown].
 *
 * @param getAvailable resolves the bindable speakers whenever the dropdown is (re)opened, so the
 *   list reflects live [com.dreamdisplays.platform.client.displays.SpeakerRegistry] state.
 * @param onSpeaker invoked with the chosen speaker id.
 */
class SpeakerDropdown(
    private val getAvailable: () -> List<SpeakerInfo>,
    private val onSpeaker: (UUID) -> Unit,
) {
    private var visible = false
    private var items: List<SpeakerInfo> = emptyList()
    private var rect = UiRect(0, 0, WIDTH, ITEM_H)
    private var animProgress = 0f
    private var lastFrameNanos = 0L
    private var hovered = -1

    fun isVisible(): Boolean = visible

    /** (Re)populates the item list and shows the dropdown below ([anchorX], [anchorY]). */
    fun show(anchorX: Int, anchorY: Int) {
        items = getAvailable()
        visible = true
        lastFrameNanos = 0L
        anchor(anchorX, anchorY)
    }

    fun hide() {
        visible = false
    }

    private fun anchor(anchorX: Int, anchorY: Int) {
        val height = if (items.isEmpty()) EMPTY_H else ITEM_H * items.size
        rect = UiRect(anchorX, anchorY + 4, WIDTH, height)
    }

    /** Draws the dropdown at ([anchorX], [anchorY]); positions are refreshed every frame. */
    fun draw(g: GuiGraphicsCompat, anchorX: Int, anchorY: Int, mouseX: Int, mouseY: Int) {
        if (!visible) return
        anchor(anchorX, anchorY)

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.016f else ((now - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameNanos = now
        animProgress += (1f - animProgress) * minOf(1f, dt * 12f)
        if (animProgress < 0.01f) animProgress = 0f

        g.drawPanelSprite(rect, DROPDOWN_SPRITE, animProgress)
        hovered = if (rect.contains(mouseX, mouseY)) {
            ((mouseY - rect.y) / ITEM_H).coerceIn(0, items.size - 1)
        } else {
            -1
        }

        val font = Minecraft.getInstance().font
        if (items.isEmpty()) {
            val color = scaleAlpha(UiTheme.TEXT_DIM, animProgress)
            g.drawText(
                font,
                Component.translatable("dreamdisplays.ui.speaker.none"),
                rect.x + 6,
                rect.y + (rect.h - font.lineHeight) / 2,
                color,
                false,
            )
            return
        }
        items.forEachIndexed { i, s ->
            val itemY = rect.y + ITEM_H * i
            if (i == hovered) {
                g.fill(rect.x + 1, itemY, rect.x + WIDTH - 1, itemY + ITEM_H, scaleAlpha(UiTheme.HOVER_FILL, animProgress))
                g.drawOutline(UiRect(rect.x + 1, itemY, WIDTH - 2, ITEM_H), scaleAlpha(UiTheme.CARD_BORDER_HOVER, animProgress))
            }
            val color = scaleAlpha(if (i == hovered) UiTheme.TEXT_PRIMARY else UiTheme.TEXT_DIM, animProgress)
            val label = UiText.trim(font, s.name + " (" + s.id.toString().substring(0, 8) + ")", WIDTH - 12)
            g.drawText(font, label, rect.x + 6, itemY + (ITEM_H - font.lineHeight) / 2, color, false)
        }
    }

    /** Handles a left click while visible: picks the clicked item or dismisses. Returns true when consumed. */
    fun handleClick(mx: Int, my: Int): Boolean {
        if (!visible) return false
        val inside = rect.contains(mx, my)
        visible = false
        if (!inside || items.isEmpty()) return false
        val index = ((my - rect.y) / ITEM_H).coerceIn(0, items.size - 1)
        val s = SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f)
        Minecraft.getInstance().soundManager.play(s)
        onSpeaker(items[index].id)
        return true
    }

    private companion object {
        const val WIDTH = 200
        const val ITEM_H = 18
        const val EMPTY_H = 30
    }
}
