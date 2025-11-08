package ru.oftendev.recipebook

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.command.impl.PluginCommand
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.registry.event.RegistryEvents
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import ru.oftendev.recipebook.category.RecipeCategories
import ru.oftendev.recipebook.commands.MainCommand
import ru.oftendev.recipebook.integration.ShopIntegration
import ru.oftendev.recipebook.integration.VaultPackIntegration

lateinit var recipeBookPlugin: RecipeBookPlugin
    private set

class RecipeBookPlugin: EcoPlugin() {
    init {
        recipeBookPlugin = this
    }

    override fun handleEnable() {
        ShopIntegration.init(this)
        VaultPackIntegration.init(this)
        RecipeCategories.reload()
    }

    override fun handleReload() {
        RecipeCategories.reload()
    }

    override fun loadPluginCommands(): MutableList<PluginCommand> {
        return mutableListOf(
            MainCommand(this)
        )
    }
}

fun makesound(string: String?): Sound? {
    return string?.let {
        Sound.sound(Key.key(it), Sound.Source.AMBIENT, 1.0f, 1.0f)
    }
}