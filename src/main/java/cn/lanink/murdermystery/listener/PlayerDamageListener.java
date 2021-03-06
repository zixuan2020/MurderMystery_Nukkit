package cn.lanink.murdermystery.listener;

import cn.lanink.murdermystery.MurderMystery;
import cn.lanink.murdermystery.entity.EntityPlayerCorpse;
import cn.lanink.murdermystery.event.MurderPlayerDamageEvent;
import cn.lanink.murdermystery.event.MurderPlayerDeathEvent;
import cn.lanink.murdermystery.room.GameMode;
import cn.lanink.murdermystery.room.Room;
import cn.lanink.murdermystery.utils.Language;
import cn.lanink.murdermystery.utils.Tools;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.Sound;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;

import java.util.Random;

public class PlayerDamageListener implements Listener {

    private final MurderMystery murderMystery;
    private final Language language;

    public PlayerDamageListener(MurderMystery murderMystery) {
        this.murderMystery = murderMystery;
        this.language = murderMystery.getLanguage();
    }

    /**
     * 实体受到另一实体伤害事件
     * @param event 事件
     */
    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();
            Player player = (Player) event.getEntity();
            if (damager == null || player == null) {
                return;
            }
            Room room = this.murderMystery.getRooms().getOrDefault(damager.getLevel().getName(), null);
            if (room == null) return;
            event.setCancelled(true);
            if (event instanceof  EntityDamageByChildEntityEvent) {
                EntityDamageByChildEntityEvent event1 = (EntityDamageByChildEntityEvent) event;
                damager = (Player) event1.getDamager();
                player = (Player) event1.getEntity();
                Entity child = event1.getChild();
                if (child == null || child.namedTag == null) return;
                if (child.namedTag.getBoolean("isMurderItem")) {
                    if (child.namedTag.getInt("MurderType") == 20) {
                        Server.getInstance().getPluginManager().callEvent(new MurderPlayerDamageEvent(room, damager, player));
                    }else if (child.namedTag.getInt("MurderType") == 23) {
                        Tools.addSound(player, Sound.RANDOM_ANVIL_LAND);
                        player.sendMessage(this.language.damageSnowball);
                        Effect effect = Effect.getEffect(2);
                        effect.setAmplifier(2);
                        effect.setDuration(40);
                        player.addEffect(effect);
                    }
                }
            }else {
                if (room.isPlaying(damager) && room.getPlayerMode(damager) == 3 &&
                        room.isPlaying(player) && room.getPlayerMode(player) != 0) {
                    if (damager.getInventory().getItemInHand() != null) {
                        CompoundTag tag = damager.getInventory().getItemInHand().getNamedTag();
                        if (tag != null && tag.getBoolean("isMurderItem") && tag.getInt("MurderType") == 2) {
                            Server.getInstance().getPluginManager().callEvent(new MurderPlayerDamageEvent(room, damager, player));
                        }
                    }
                }
            }
        }
    }

    /**
     * 伤害事件
     * @param event 事件
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            Room room = this.murderMystery.getRooms().getOrDefault(player.getLevel().getName(), null);
            if (room == null) return;
            //虚空 游戏开始前拉回 游戏中判断玩家死亡
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                if (room.getMode() == 2) {
                    Server.getInstance().getPluginManager().callEvent(new MurderPlayerDeathEvent(room, player));
                }else {
                    player.teleport(room.getWaitSpawn());
                }
            }
            event.setCancelled(true);
        }else if (event.getEntity() instanceof EntityPlayerCorpse) {
            event.setCancelled(true);
        }
    }

    /**
     * 玩家被攻击事件(符合游戏条件的攻击)
     * @param event 事件
     */
    @EventHandler
    public void onPlayerDamage(MurderPlayerDamageEvent event) {
        if (!event.isCancelled()) {
            Player damage = event.getDamage();
            Player player = event.getPlayer();
            Room room = event.getRoom();
            if (damage == null || player == null || room == null) return;
            if (room.getGameMode() == GameMode.INFECTED) {
                if (room.getPlayerMode(damage) == 3) {
                    if (room.getPlayerMode(player) == 3) {
                        return;
                    }
                    room.addPlaying(player, 3);
                    player.sendTitle(this.language.titleKillerTitle,
                            this.language.titleKillerSubtitle, 10, 40, 10);
                }else {
                    if (room.getPlayerMode(player) != 3) {
                        return;
                    }
                }
                player.getLevel().addSound(player, Sound.GAME_PLAYER_HURT);
                player.teleport(room.getRandomSpawn().get(new Random().nextInt(room.getRandomSpawn().size())));
                player.getInventory().clearAll();
                player.getInventory().setItem(1, Tools.getMurderItem(2));
                player.addEffect(Effect.getEffect(2).setAmplifier(2).setDuration(60));
            }else {
                //攻击者是杀手
                if (room.getPlayerMode(damage) == 3) {
                    damage.sendMessage(this.language.killPlayer);
                    player.sendTitle(this.language.deathTitle,
                            this.language.deathByKillerSubtitle, 20, 60, 20);
                }else { //攻击者是平民或侦探
                    if (room.getPlayerMode(player) == 3) {
                        damage.sendMessage(this.language.killKiller);
                        room.killKillerPlayer = damage;
                        player.sendTitle(this.language.deathTitle,
                                this.language.killerDeathSubtitle, 10, 20, 20);
                    } else {
                        damage.sendTitle(this.language.deathTitle,
                                this.language.deathByDamageTeammateSubtitle, 20, 60, 20);
                        player.sendTitle(this.language.deathTitle,
                                this.language.deathByTeammateSubtitle, 20, 60, 20);
                        Server.getInstance().getPluginManager().callEvent(new MurderPlayerDeathEvent(room, damage));
                    }
                }
                Server.getInstance().getPluginManager().callEvent(new MurderPlayerDeathEvent(room, player));
            }
        }
    }

}
