package ru.oftendev.recipebook.integration

import com.vaultpack.VaultPackPlugin
import com.vaultpack.managers.BackpackTypeManager
import com.vaultpack.types.BackpackType
import com.willfp.eco.core.items.CustomItem
import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem
import com.willfp.eco.core.items.provider.ItemProvider
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import ru.oftendev.recipebook.RecipeBookPlugin
import java.util.*

/**
 * Integration with VaultPack plugin for custom backpack items
 * Allows RecipeBook to recognize and use VaultPack items in recipes
 */
object VaultPackIntegration {
    private var pluginAvailable = false
    private var vaultPackPlugin: VaultPackPlugin? = null
    private var backpackTypeManager: BackpackTypeManager? = null

    /**
     * Initialize the VaultPack integration
     */
    fun init(plugin: RecipeBookPlugin) {
        pluginAvailable = Bukkit.getPluginManager().isPluginEnabled("VaultPack")

        if (pluginAvailable) {
            try {
                vaultPackPlugin = VaultPackPlugin.getInstance()
                backpackTypeManager = vaultPackPlugin?.backpackTypeManager

                // Register VaultPack item provider with eco
                Items.registerItemProvider(VaultPackItemProvider())

                Bukkit.getLogger().info("[RecipeBook] VaultPack integration enabled")
            } catch (e: Exception) {
                Bukkit.getLogger().warning("[RecipeBook] Failed to initialize VaultPack integration: ${e.message}")
                pluginAvailable = false
            }
        }
    }

    /**
     * Check if VaultPack integration is available
     */
    fun isEnabled(): Boolean = pluginAvailable

    /**
     * Get a backpack type by ID
     */
    fun getBackpackType(id: String): BackpackType? {
        return backpackTypeManager?.getBackpackType(id)
    }

    /**
     * Get all registered backpack types
     */
    fun getAllBackpackTypes(): Map<String, BackpackType> {
        return backpackTypeManager?.allBackpackTypes ?: emptyMap()
    }

    /**
     * Get recipe for a backpack type
     * Returns a list of 9 ItemStacks representing the crafting grid
     */
    fun getBackpackRecipe(backpackId: String): List<ItemStack>? {
        val backpackType = getBackpackType(backpackId) ?: return null
        if (!backpackType.hasRecipe()) return null

        val recipeStrings = backpackType.recipe
        if (recipeStrings.size != 9) return null

        return recipeStrings.map { recipeString ->
            parseRecipeItem(recipeString)
        }
    }

    /**
     * Parse a recipe item string from VaultPack format
     * Format: "material amount" or "material"
     */
    private fun parseRecipeItem(recipeString: String): ItemStack {
        if (recipeString.isBlank()) {
            return ItemStack(Material.AIR)
        }

        val parts = recipeString.trim().split(" ")
        val materialName = parts[0]
        val amount = if (parts.size > 1) parts[1].toIntOrNull() ?: 1 else 1

        // Try to parse as vanilla material
        val material = try {
            Material.valueOf(materialName.uppercase())
        } catch (e: IllegalArgumentException) {
            // Could be a custom item from eco plugins
            return Items.lookup(materialName).item.apply {
                this.amount = amount
            }
        }

        return ItemStack(material, amount)
    }
}

/**
 * Custom item provider for VaultPack backpacks
 * Allows eco's Items.lookup() to recognize "vaultpack:backpack_id" format
 */
class VaultPackItemProvider : ItemProvider("vaultpack") {

    override fun provideForKey(key: String): TestableItem? {
        val backpackType = VaultPackIntegration.getBackpackType(key) ?: return null
        return VaultPackCustomItem(key, backpackType)
    }
}

/**
 * Custom item implementation for VaultPack backpacks
 */
class VaultPackCustomItem(
    private val backpackId: String,
    private val backpackType: BackpackType
) : CustomItem(
    namespacedKeyOf("vaultpack", backpackId),
    { item -> isVaultPackItem(item, backpackId) },
    createBackpackItemStack(backpackType, backpackId)
) {
    companion object {
        /**
         * Check if an ItemStack is a VaultPack backpack of the specified type
         */
        private fun isVaultPackItem(item: ItemStack, backpackId: String): Boolean {
            val meta = item.itemMeta ?: return false
            val pdc = meta.persistentDataContainer

            // Check for VaultPack's backpack type ID in PDC
            // VaultPack uses "backpack_type" as the key
            val key = NamespacedKey(
                VaultPackPlugin.getInstance(),
                "backpack_type"
            )

            val storedId = pdc.get(key, PersistentDataType.STRING)
            return storedId == backpackId
        }

        /**
         * Helper to create NamespacedKey
         */
        private fun namespacedKeyOf(namespace: String, key: String): NamespacedKey {
            return NamespacedKey(namespace, key)
        }

        /**
         * Create an ItemStack from a BackpackType
         * Based on VaultPack's BackpackCommand.createBackpackItem()
         */
        private fun createBackpackItemStack(type: BackpackType, typeId: String): ItemStack {
            val item = ItemStack(type.material, 1)
            val meta = item.itemMeta ?: return item

            // Handle player head with custom texture
            if (type.material == Material.PLAYER_HEAD && type.hasTexture()) {
                val skullMeta = meta as SkullMeta
                applyTexture(skullMeta, type.texture)
            }

            // Set display name using legacy color codes
            val serializer = LegacyComponentSerializer.legacyAmpersand()
            meta.displayName(serializer.deserialize(type.displayName))

            // Set lore
            val lore = type.lore.map { line ->
                val formatted = line
                    .replace("%tier%", type.defaultTier.displayName)
                    .replace("%size%", type.defaultTier.size.toString())
                    .replace("%used%", "0")
                serializer.deserialize(formatted)
            }
            meta.lore(lore)

            // Add custom model data
            if (type.customModelData > 0) {
                meta.setCustomModelData(type.customModelData)
            }

            // Add glow effect
            if (type.hasGlow()) {
                meta.addEnchant(Enchantment.LURE, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }

            // Store backpack type ID in PDC
            // VaultPack uses "backpack_type" as the key
            val pdc = meta.persistentDataContainer
            pdc.set(
                NamespacedKey(VaultPackPlugin.getInstance(), "backpack_type"),
                PersistentDataType.STRING,
                typeId
            )

            item.itemMeta = meta
            return item
        }

        /**
         * Apply texture to skull meta
         * Based on VaultPack's BackpackCommand.applyTexture()
         */
        private fun applyTexture(skullMeta: SkullMeta, texture: String) {
            try {
                // Use Bukkit's profile API (Paper 1.18.2+)
                val profile = Bukkit.createPlayerProfile(UUID.randomUUID())
                val textures = profile.textures

                // Decode the base64 texture to get the URL
                val decoded = String(java.util.Base64.getDecoder().decode(texture))
                val urlStart = decoded.indexOf("\"url\":\"") + 7
                val urlEnd = decoded.lastIndexOf("\"")
                val url = decoded.substring(urlStart, urlEnd)

                textures.skin = java.net.URL(url)
                profile.setTextures(textures)
                skullMeta.setOwnerProfile(profile)
            } catch (e: Exception) {
                // Fallback to reflection method for older versions
                try {
                    val gameProfileClass = Class.forName("com.mojang.authlib.GameProfile")
                    val propertyClass = Class.forName("com.mojang.authlib.properties.Property")

                    val profile = gameProfileClass.getConstructor(UUID::class.java, String::class.java)
                        .newInstance(UUID.randomUUID(), null)
                    val properties = gameProfileClass.getMethod("getProperties").invoke(profile)

                    val property = propertyClass.getConstructor(String::class.java, String::class.java)
                        .newInstance("textures", texture)

                    val putMethod = properties.javaClass.getMethod("put", Any::class.java, Any::class.java)
                    putMethod.invoke(properties, "textures", property)

                    val profileField = skullMeta.javaClass.getDeclaredField("profile")
                    profileField.isAccessible = true
                    profileField.set(skullMeta, profile)
                } catch (reflectionException: Exception) {
                    Bukkit.getLogger().warning("[RecipeBook] Failed to apply VaultPack backpack texture: ${reflectionException.message}")
                }
            }
        }
    }
}
