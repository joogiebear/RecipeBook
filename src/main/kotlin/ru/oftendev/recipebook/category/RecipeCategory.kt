package ru.oftendev.recipebook.category

import com.google.common.collect.BiMap
import com.willfp.eco.core.config.config
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.builder.ItemStackBuilder
import com.willfp.eco.core.recipe.Recipes
import com.willfp.eco.core.recipe.parts.EmptyTestableItem
import com.willfp.eco.core.recipe.recipes.CraftingRecipe
import com.willfp.eco.util.containsIgnoreCase
import com.willfp.eco.util.namespacedKeyOf
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.RecipeChoice.ExactChoice
import org.bukkit.inventory.RecipeChoice.MaterialChoice
import org.bukkit.inventory.ShapedRecipe
import ru.oftendev.recipebook.gui.CategoryCategoryGUI
import ru.oftendev.recipebook.gui.ItemCategoryGUI
import ru.oftendev.recipebook.recipeBookPlugin
import java.lang.reflect.Field

class RecipeCategory(val config: Config) {
    val id = config.getString("id")
    val type = config.getString("type")

    val icon = config.getSubsectionOrNull("icon")?.let { CategoryIcon(it) }

    val namespaces = config.getStrings("namespaces")

    val items = config.getSubsections("items").map { CategoryStack(this, it) }

    val categories = config.getStrings("categories")

    val parsedCategories: List<RecipeCategory>
        get() = categories.mapNotNull { RecipeCategories.getById(it) }

    val gui = if (type == "items") ItemCategoryGUI(config.getSubsection("gui"), this)
        else CategoryCategoryGUI(config.getSubsection("gui"), this)

    fun getMemberItems(): List<ItemStack> {
        return when(type) {
            "items" -> mutableListOf()
            else -> {
                categories.mapNotNull { RecipeCategories.getById(it)?.icon?.getItemStack() }
            }
        }
    }

    fun getMemberItemsRecipes(player: Player): List<ItemStack> {
        return items.mapNotNull {
            if (canCraft(player, it.item.item)) {
                it.item.item
            } else it.noPermItem
        }
    }
}

fun getRecipesBiMap(): BiMap<NamespacedKey, CraftingRecipe> {
    val field: Field = Recipes::class.java.getDeclaredField("RECIPES")
    field.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    return field.get(null) as BiMap<NamespacedKey, CraftingRecipe>
}

fun canCraft(player: Player, itemStack: ItemStack): Boolean {
    return Items.getCustomItem(itemStack)?.let { player.hasPermission(Recipes.getRecipe(it.key)?.permission
        ?: return true) } ?: true
}

fun getCustomItemRecipe(item: CustomItem): List<ItemStack>? {
    recipeBookPlugin.logger.info { "Checking custom item ${item.key}" }
    val allRecipes = getRecipesBiMap().values
    val foundRecipe = allRecipes.firstOrNull { item.test(it.output) }

    recipeBookPlugin.logger.info { "Found recipe ${foundRecipe?.key}" }

    if (foundRecipe != null) {
        val foundEcoRecipe = Recipes.getRecipe(foundRecipe.key) ?: return null
        recipeBookPlugin.logger.info { "Found eco recipe ${foundEcoRecipe.key}" }
        return foundEcoRecipe.parts.map { if (it is EmptyTestableItem) ItemStack(Material.AIR) else it.item }
    }

    return null
}

fun getVanillaRecipe(stack: ItemStack): List<ItemStack> {
    val recipe = Bukkit.getRecipesFor(stack)
        .filterIsInstance<ShapedRecipe>()
        .first()

    val result = mutableListOf<ItemStack>()
    for (s in recipe.shape) {
        for (c in s.toCharArray()) {
            result += recipe.choiceMap[c].displayIcon
        }
    }
    return result
}

fun getRecipe(itemStack: ItemStack): List<ItemStack>? {
    val customItem = Items.getCustomItem(itemStack)

    if (customItem != null) {
        return getCustomItemRecipe(customItem) ?: getVanillaRecipe(itemStack)
    }

    return getVanillaRecipe(itemStack)

//    return Items.getCustomItem(itemStack)?.let { customItem -> Recipes
//        .getRecipe(namespacedKeyOf(customItem.key.namespace, customItem.key.key.removePrefix("set_")))
//        ?.let { recipe -> recipe.parts.map { if (it is EmptyTestableItem) ItemStack(Material.AIR) else it.item }
//            } } ?: run {
//        Bukkit.getRecipesFor(itemStack)
//            .filterIsInstance<ShapedRecipe>()
//            .firstOrNull()?.let {
//                val result = mutableListOf<ItemStack>()
//                for (s in it.shape) {
//                    for (c in s.toCharArray()) {
//                        result += it.choiceMap[c]!!.displayIcon
//                    }
//                }
//                result
//        }
//    }
}

class CategoryStack(private val parent: RecipeCategory,
                    private val config: Config) {
    val item = Items.lookup(config.getString("item"))
    val displayNoPerm = config.getBool("display-no-perm")
    val noPermItem = config.getSubsectionOrNull("no-perm-item")?.let {
        ItemStackBuilder(Items.lookup(it.getString("item")))
            .setDisplayName(it.getFormattedString("name"))
            .addLoreLines(it.getFormattedStrings("lore"))
            .build()
    }
        get() = field?.clone()
}

val RecipeChoice?.displayIcon
    get() = when(this) {
        is ExactChoice -> this.choices.firstOrNull() ?: ItemStack(Material.AIR)
        is MaterialChoice -> this.choices.firstOrNull()?.let { ItemStack(it) } ?: ItemStack(Material.AIR)
        else -> this?.itemStack ?: ItemStack(Material.AIR)
    }

class CategoryIcon(private val config: Config) {
    fun getItemStack(): ItemStack {
        return ItemStackBuilder(
            Items.lookup(config.getString("item"))
        )
            .addLoreLines(config.getStrings("lore"))
            .build()
    }
}