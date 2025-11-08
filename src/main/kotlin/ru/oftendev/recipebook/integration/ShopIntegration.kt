package ru.oftendev.recipebook.integration

import com.willfp.ecoshop.shop.BuyStatus
import com.willfp.ecoshop.shop.BuyType
import com.willfp.ecoshop.shop.ShopItems
import com.willfp.ecoshop.shop.getDisplay
import com.willfp.ecoshop.shop.shopItem
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Integration with EcoShop plugin for purchasing missing recipe materials
 */
object ShopIntegration {
    private var pluginAvailable = false
    private var configEnabled = false
    private var showPrices = true

    /**
     * Initialize the shop integration
     */
    fun init(plugin: ru.oftendev.recipebook.RecipeBookPlugin) {
        pluginAvailable = Bukkit.getPluginManager().isPluginEnabled("EcoShop")
        configEnabled = plugin.configYml.getBool("shop-integration.enabled")
        showPrices = plugin.configYml.getBool("shop-integration.show-prices")

        if (pluginAvailable && configEnabled) {
            Bukkit.getLogger().info("[RecipeBook] EcoShop integration enabled")
        } else if (pluginAvailable && !configEnabled) {
            Bukkit.getLogger().info("[RecipeBook] EcoShop found but integration disabled in config")
        }
    }

    /**
     * Check if shop integration is available and enabled
     */
    fun isEnabled(): Boolean = pluginAvailable && configEnabled

    /**
     * Check if prices should be shown in lore
     */
    fun shouldShowPrices(): Boolean = isEnabled() && showPrices

    /**
     * Get information about a material's availability in the shop
     */
    fun getMaterialShopInfo(player: Player, material: ItemStack, amountNeeded: Int): MaterialShopInfo? {
        if (!isEnabled()) return null

        // Create a sample item to check against shop
        val sampleItem = material.clone().apply { amount = 1 }
        val shopItem = sampleItem.shopItem ?: return null

        // Check if the item is buyable
        if (!shopItem.isBuyable) return null

        // Get buy status for the amount needed
        val buyStatus = shopItem.getBuyStatus(player, amountNeeded, BuyType.NORMAL)
        val price = shopItem.buyPrice ?: return null

        return MaterialShopInfo(
            shopItem = shopItem,
            amountNeeded = amountNeeded,
            canAfford = buyStatus == BuyStatus.ALLOW,
            buyStatus = buyStatus,
            priceDisplay = price.getDisplay(player, amountNeeded)
        )
    }

    /**
     * Attempt to purchase missing materials from the shop
     */
    fun purchaseMaterials(player: Player, materials: List<Pair<ItemStack, Int>>): PurchaseResult {
        if (!isEnabled()) {
            return PurchaseResult(false, "Shop integration not available")
        }

        val missingItems = mutableListOf<String>()
        val cannotAffordItems = mutableListOf<String>()
        val purchaseList = mutableListOf<Pair<com.willfp.ecoshop.shop.ShopItem, Int>>()

        // First, validate all materials can be purchased
        for ((material, amount) in materials) {
            val sampleItem = material.clone().apply { this.amount = 1 }
            val shopItem = sampleItem.shopItem

            if (shopItem == null || !shopItem.isBuyable) {
                missingItems.add(material.type.name)
                continue
            }

            val buyStatus = shopItem.getBuyStatus(player, amount, BuyType.NORMAL)
            when (buyStatus) {
                BuyStatus.ALLOW -> {
                    purchaseList.add(shopItem to amount)
                }
                BuyStatus.CANNOT_AFFORD -> {
                    cannotAffordItems.add("${material.type.name} x$amount")
                }
                else -> {
                    missingItems.add("${material.type.name} (${buyStatus.name})")
                }
            }
        }

        // If any items can't be purchased, return error
        if (missingItems.isNotEmpty()) {
            return PurchaseResult(false, "Cannot buy from shop: ${missingItems.joinToString(", ")}")
        }

        if (cannotAffordItems.isNotEmpty()) {
            return PurchaseResult(false, "Cannot afford: ${cannotAffordItems.joinToString(", ")}")
        }

        // Purchase all items
        for ((shopItem, amount) in purchaseList) {
            try {
                shopItem.buy(player, amount, BuyType.NORMAL)
            } catch (e: Exception) {
                return PurchaseResult(false, "Purchase failed: ${e.message}")
            }
        }

        return PurchaseResult(true, "Successfully purchased all materials!")
    }
}

/**
 * Information about a material's availability in the shop
 */
data class MaterialShopInfo(
    val shopItem: com.willfp.ecoshop.shop.ShopItem,
    val amountNeeded: Int,
    val canAfford: Boolean,
    val buyStatus: BuyStatus,
    val priceDisplay: String
)

/**
 * Result of attempting to purchase materials
 */
data class PurchaseResult(
    val success: Boolean,
    val message: String
)
