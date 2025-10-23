package ru.oftendev.recipebook.gui

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.gui.menu.Menu
import com.willfp.eco.core.gui.player
import com.willfp.eco.core.gui.slot.ConfigSlot
import com.willfp.eco.core.gui.slot.FillerMask
import com.willfp.eco.core.gui.slot.MaskItems
import com.willfp.eco.core.gui.slot.Slot
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.items.builder.modify
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.oftendev.recipebook.category.canCraft
import ru.oftendev.recipebook.category.getRecipe
import ru.oftendev.recipebook.makesound
import ru.oftendev.recipebook.recipeBookPlugin

class RecipeGUI(val config: Config, val stack: ItemStack) {
    fun open(player: Player, parent: Menu?) {
        val items = getRecipe(stack)!!
        val pattern = config.getStrings("mask.pattern")
        val menu = Menu.builder(pattern.size)
            .setTitle(config.getFormattedString("title"))
        var row = 1
        var num = 0
        pattern.forEach {
            var col = 1
            it.toCharArray().forEach {
                    s -> kotlin.run {
                if (s.equals('i', true)) {
                    if (num < items.size) {
                        if (!items[num].type.isAir) {
                            menu.setSlot(row, col, slot(items[num], makesound(config
                                .getStringOrNull("buttons.back.click_sound")), true))
                        }
                    }
                    num++
                }
                if (s.equals('o', true)) {
                    menu.setSlot(row, col, slot(stack,
                        makesound(config.getStringOrNull("buttons.slot.click_sound")), false))
                }
            }
                col++
            }
            row++
        }
        menu.setMask(
            FillerMask(
                MaskItems.fromItemNames(config.getStrings("mask.items")),
                *pattern.toTypedArray()
            )
        )
        config.getSubsectionOrNull("buttons.back")?.let {
            parent?.let {
                menu.addComponent(
                    config.getInt("buttons.back.row"),
                    config.getInt("buttons.back.column"),
                    backSlot(parent, makesound(config.getStringOrNull("buttons.back.click_sound")))
                )
            }
        }

        for (config in config.getSubsections("custom-slots")) {
            menu.setSlot(
                config.getInt("row"),
                config.getInt("column"),
                ConfigSlot(config)
            )
        }

        menu.build().open(player)
    }

    private fun backSlot(menu: Menu, sound: Sound?): Slot {
        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.back.item")))
                .addLoreLines(config.getFormattedStrings("buttons.back.lore"))
                .build()
        )
            .onLeftClick { t, _ ->
                menu.open(t.whoClicked as Player)
                if (sound != null) {
                    t.player.playSound(sound)
                }
            }
            .build()
    }

    private fun slot(item: ItemStack, sound: Sound?, recipe: Boolean): Slot {
        return Slot.builder(
            ItemStackBuilder(item.clone())
                .addLoreLines(
                    config.getFormattedStrings("buttons.recipe-parts-lore")
                )
                .build()
        )
            .onLeftClick { t, _, m ->
                getRecipe(item) ?: return@onLeftClick
                if (recipe) {
                    val itm = item.clone().modify { this.setAmount(1) }
                    if (canCraft(t.whoClicked as Player, itm)) {
                        RecipeGUI(recipeBookPlugin.configYml.getSubsection("craft-gui"), itm)
                            .open(t.whoClicked as Player, m)
                    }
                }
                if (sound != null) {
                    t.player.playSound(sound)
                }
            }
            .build()
    }
}