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
import org.bukkit.Material
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

        config.getSubsectionOrNull("buttons.quick-craft")?.let {
            menu.addComponent(
                config.getInt("buttons.quick-craft.row"),
                config.getInt("buttons.quick-craft.column"),
                quickCraftSlot(
                    player,
                    items,
                    makesound(config.getStringOrNull("buttons.quick-craft.success_sound")),
                    makesound(config.getStringOrNull("buttons.quick-craft.fail_sound"))
                )
            )
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

    private fun quickCraftSlot(player: Player, items: List<ItemStack>, successSound: Sound?, failSound: Sound?): Slot {
        val materialCounts = checkMaterials(player, items)
        val hasAllMaterials = materialCounts.all { it.second >= it.third }

        val loreLines = config.getFormattedStrings("buttons.quick-craft.lore").toMutableList()
        val materialsLoreIndex = loreLines.indexOfFirst { it.contains("%materials%") }

        if (materialsLoreIndex != -1) {
            loreLines.removeAt(materialsLoreIndex)
            val materialLines = materialCounts.map { (item, has, needs) ->
                val color = if (has >= needs) "&a" else "&c"
                val itemName = if (item.hasItemMeta() && item.itemMeta.hasDisplayName()) {
                    item.itemMeta.displayName
                } else {
                    item.type.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
                }
                "$color  $has/$needs &7$itemName"
            }
            loreLines.addAll(materialsLoreIndex, materialLines)
        }

        return Slot.builder(
            ItemStackBuilder(Items.lookup(config.getString("buttons.quick-craft.item")))
                .addLoreLines(loreLines)
                .build()
        )
            .onLeftClick { t, _ ->
                val p = t.whoClicked as Player
                if (hasAllMaterials) {
                    if (craftItem(p, items)) {
                        p.sendMessage(
                            recipeBookPlugin.langYml.getFormattedString("messages.craft-success")
                                .replace("%item%", stack.itemMeta.displayName)
                        )
                        if (successSound != null) {
                            t.player.playSound(successSound)
                        }
                        p.closeInventory()
                    } else {
                        p.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-insufficient"))
                        if (failSound != null) {
                            t.player.playSound(failSound)
                        }
                    }
                } else {
                    p.sendMessage(recipeBookPlugin.langYml.getFormattedString("messages.craft-insufficient"))
                    if (failSound != null) {
                        t.player.playSound(failSound)
                    }
                }
            }
            .build()
    }

    private fun checkMaterials(player: Player, items: List<ItemStack>): List<Triple<ItemStack, Int, Int>> {
        val materialMap = mutableMapOf<Material, MutableList<ItemStack>>()

        // Group items by material type
        items.forEach { item ->
            if (!item.type.isAir) {
                materialMap.getOrPut(item.type) { mutableListOf() }.add(item)
            }
        }

        val results = mutableListOf<Triple<ItemStack, Int, Int>>()

        materialMap.forEach { (material, itemList) ->
            val needed = itemList.sumOf { it.amount }
            val has = player.inventory.all(material).values.sumOf { it.amount }
            results.add(Triple(itemList.first(), has, needed))
        }

        return results
    }

    private fun craftItem(player: Player, items: List<ItemStack>): Boolean {
        val materialCounts = checkMaterials(player, items)

        // Double check materials
        if (!materialCounts.all { it.second >= it.third }) {
            return false
        }

        // Remove materials from inventory
        items.forEach { item ->
            if (!item.type.isAir) {
                player.inventory.removeItem(item)
            }
        }

        // Give the crafted item
        player.inventory.addItem(stack.clone())

        return true
    }
}