package com.ghostchu.quickshop.shop.signhooker;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SignHooker {

  private final QuickShop PLUGIN;
  private final ProtocolManager PROTOCOL_MANAGER = ProtocolLibrary.getProtocolManager();
  private PacketAdapter chunkAdapter;

  public SignHooker(final QuickShop plugin) {

    PLUGIN = plugin;
    registerListener();
  }

  public void registerListener() {

    chunkAdapter = new PacketAdapter(PLUGIN.getJavaPlugin(), ListenerPriority.HIGH, PacketType.Play.Server.MAP_CHUNK) {
      @Override
      public void onPacketSending(@NotNull final PacketEvent event) {
        //is really full chunk data
        //In 1.17, this value was removed, so read safely
        final Boolean boxedIsFull = event.getPacket().getBooleans().readSafely(0);
        final boolean isFull = boxedIsFull == null || boxedIsFull;
        if(!isFull) {
          return;
        }
        final Player player = event.getPlayer();
        if(player == null || !player.isOnline()) {
          return;
        }
        if(player.getClass().getName().contains("TemporaryPlayer")) {
          return;
        }
        final StructureModifier<Integer> integerStructureModifier = event.getPacket().getIntegers();
        //chunk x
        final int x = integerStructureModifier.read(0);
        //chunk z
        final int z = integerStructureModifier.read(1);

        final Map<Location, Shop> shops = PLUGIN.getShopManager().getShops(player.getWorld().getName(), x, z);
        if(shops == null) {
          return;
        }
        QuickShop.folia().getImpl().runLater(()->shops.forEach((loc, shop)->updatePerPlayerShopSign(player, loc, shop)), 2);
      }
    };

    PROTOCOL_MANAGER.addPacketListener(chunkAdapter);
    Log.debug("SignHooker chunk adapter registered.");
  }

  public void updatePerPlayerShopSign(final Player player, final Location location, final Shop shop) {

    Util.ensureThread(false);
    if(!shop.isLoaded()) {
      return;
    }
    if(!Util.isLoaded(location)) {
      return;
    }
    Log.debug("Updating per-player packet sign: Player=" + player.getName() + ", Location=" + location + ", Shop=" + shop.getShopId());
    final List<Component> lines = shop.getSignText(PLUGIN.getTextManager().findRelativeLanguages(player));
    for(final Sign sign : shop.getSigns()) {
      PLUGIN.getPlatform().sendSignTextChange(player, sign, PLUGIN.getConfig().getBoolean("shop.sign-glowing"), lines);
    }
  }

  public void unload() {

    if(PROTOCOL_MANAGER == null) {
      return;
    }
    if(this.chunkAdapter != null) {
      PROTOCOL_MANAGER.removePacketListener(chunkAdapter);
    }
  }

  public void updatePerPlayerShopSignBroadcast(final Location location, final Shop shop) {

    final World world = shop.getLocation().getWorld();
    if(world == null) {
      return;
    }
    final Collection<Entity> nearbyPlayers = world.getNearbyEntities(shop.getLocation(), Bukkit.getViewDistance() * 16, shop.getLocation().getWorld().getMaxHeight(), Bukkit.getViewDistance() * 16);
    for(final Entity nearbyPlayer : nearbyPlayers) {
      if(nearbyPlayer instanceof Player player) {
        updatePerPlayerShopSign(player, location, shop);
      }
    }
  }
}
