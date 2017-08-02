package de.dosmike.sponge.vshop;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

public class InteractionHandler {
	public static enum Button { left, right }
	
	/** return true to cancel the event in the parent */
	static boolean clickEntity(Player source, Entity target, Button side) {
		//try to get shop:
		Location<World> tl = target.getLocation();
		Optional<NPCguard> npc = VillagerShops.getNPCfromLocation(tl);
		
		if (npc.isPresent()) {
//			VillagerShops.l("NPC: " + npc.get().getDisplayName().toPlain());
//			if (side == Button.right) {
				if (npc.get().getPreparator().size()>0) {
					source.openInventory(npc.get().getInventory(), Cause.builder().named("PLUGIN (Shop Opened)", VillagerShops.getInstance()).build());
					VillagerShops.openShops.put(source.getUniqueId(), npc.get().getIdentifier());
//				}
			}
			
			return true;
		}
		return false;
	}
	
	/** return true to cancel the event in the parent */
	static boolean clickInventory(Player source, int slot) {
		//assume width of 9
//		int column=slot%9, row=slot/9;
		
		if (!VillagerShops.openShops.containsKey(source.getUniqueId())) {
			VillagerShops.l("No openShop");
			return false;
		}
		Optional<NPCguard> shop = VillagerShops.getNPCfromShopUUID(VillagerShops.openShops.get(source.getUniqueId()));
		if (!shop.isPresent()) {
			VillagerShops.l("No NPCguard");
			return false;
		}
		InvPrep stock = shop.get().getPreparator();
		
		int type = stock.isSlotBuySell(slot);
		int index = stock.slotToIndex(slot);
		if (type < 2) {
//			stock.itemClicked(source, index, type);
			shopItemClicked(shop.get(), source, index, type);
//			ItemStack item = stock.getItem(index).getItem();
//			source.sendMessage(Text.of("You ", type==0?"bought":"sold", " ", itemcount, " ", item.get(Keys.DISPLAY_NAME).orElse(Text.of(item.getItem().getTranslation()))));
		}
		
		return true;
	}
	
	/** tries to buy or sell the item at index and returns the ammount of actuall items bought/sold
	  * use isSlotBuySell(int) to determ the actual action */
	static int shopItemClicked(NPCguard shop, Player player, int index, int buySell) {
		InvPrep prep = shop.getPreparator(); 
		if (index<0 || index>=prep.size() || buySell>1) return -1;
		
		Optional<UniqueAccount> acc = VillagerShops.getEconomy().getOrCreateAccount(player.getUniqueId());
		if (!acc.isPresent()) return 0;
		Optional<UUID> shopOwner = shop.getShopOwner();
		Optional<UniqueAccount> acc2 = shopOwner.isPresent()?VillagerShops.getEconomy().getOrCreateAccount(shopOwner.get()):Optional.empty();
		if (shopOwner.isPresent() && !acc2.isPresent()) return 0;
		
		StockItem item = prep.getItem(index);
		Currency currency = item.getCurrency();
		ShopResult result;
		double finalPrice;
		
		if (buySell==0) {
			result = item.buy(player, shop);
			if (result.getTradedItems()>0) {
				finalPrice = item.getBuyPrice()*(double)result.getTradedItems()/(double)item.getItem().getQuantity();
				acc.get().withdraw(
						currency, 
						BigDecimal.valueOf(finalPrice), 
						Cause.builder().named("PURCHASED ITEMS", VillagerShops.getInstance()).build());
				if (acc2.isPresent()) acc2.get().deposit(
						currency, 
						BigDecimal.valueOf(finalPrice), 
						Cause.builder().named("PLAYER SHOP ITEMS SOLD", VillagerShops.getInstance()).build());
				
				player.sendMessage(Text.of(TextColors.GOLD, acc.get().getBalance(currency), currency.getSymbol(), TextColors.RESET, " (",
						TextColors.RED, "-", String.format("%.2f", finalPrice), TextColors.RESET, 
						"): Buying ", TextColors.YELLOW, result.getTradedItems(), "x ", TextColors.RESET, 
						item.getItem().get(Keys.DISPLAY_NAME).orElse(Text.of(item.getItem().getType().getTranslation().get()))
						));
			} else {
				player.sendMessage(result.getMessage());
			}
		} else {
			result = item.sell(player, shop);
			if (result.getTradedItems()>0) {
				finalPrice = item.getSellPrice()*(double)result.getTradedItems()/(double)item.getItem().getQuantity();
				acc.get().deposit(
						currency, 
						BigDecimal.valueOf(finalPrice), 
						Cause.builder().named("SOLD ITEMS", VillagerShops.getInstance()).build());
				if (acc2.isPresent()) acc2.get().withdraw(
						currency, 
						BigDecimal.valueOf(finalPrice), 
						Cause.builder().named("PLAYER SHOP ITEMS BOUGHT", VillagerShops.getInstance()).build());
				
				player.sendMessage(Text.of(TextColors.GOLD, acc.get().getBalance(currency), currency.getSymbol(), TextColors.RESET, " (",
						TextColors.GREEN, "+", String.format("%.2f", finalPrice), TextColors.RESET, 
						"): Selling ", TextColors.YELLOW, result.getTradedItems(), "x ", TextColors.RESET, 
						item.getItem().get(Keys.DISPLAY_NAME).orElse(Text.of(item.getItem().getType().getTranslation().get()))
						));
			} else {
				player.sendMessage(result.getMessage());
			}
		}
		
		return result.getTradedItems();
	}
}
