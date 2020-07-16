package net.islandearth.taleofkingdoms.common.listener;

import net.islandearth.taleofkingdoms.TaleOfKingdoms;
import net.islandearth.taleofkingdoms.common.world.ConquestInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.IWorld;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.world.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Timer;
import java.util.TimerTask;

public class SleepListener extends Listener {

    @SubscribeEvent
    public void onSleep(SleepFinishedTimeEvent event) {
        ConquestInstance instance = TaleOfKingdoms.getAPI().get().getConquestInstanceStorage().getConquestInstance(Minecraft.getInstance().getIntegratedServer().getFolderName()).get();
        if (Minecraft.getInstance().isSingleplayer()) {
            PlayerEntity player = Minecraft.getInstance().player;
            if (player != null && instance.isInGuild(player)) {
                IWorld world = event.getWorld();
                if (world.getWorld().getDimension().isDaytime()) {
                    Timer timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            world.getWorld().getDimension().setWorldTime(13000);
                        }
                    }, 20);
                }
            }
        }
    }

    @SubscribeEvent
    public void onSleep(SleepingLocationCheckEvent event) {
        ConquestInstance instance = TaleOfKingdoms.getAPI().get().getConquestInstanceStorage().getConquestInstance(Minecraft.getInstance().getIntegratedServer().getFolderName()).get();
        if (event.getEntityLiving() instanceof PlayerEntity) {
            PlayerEntity playerEntity = (PlayerEntity) event.getEntityLiving();
            if (instance.isInGuild(playerEntity)) {
                event.setResult(Event.Result.ALLOW);
            }
        }
    }

    @SubscribeEvent
    public void onSleep(SleepingTimeCheckEvent event) {
        ConquestInstance instance = TaleOfKingdoms.getAPI().get().getConquestInstanceStorage().getConquestInstance(Minecraft.getInstance().getIntegratedServer().getFolderName()).get();
        if (event.getEntityLiving() instanceof PlayerEntity) {
            PlayerEntity playerEntity = (PlayerEntity) event.getEntityLiving();
            if (instance.isInGuild(playerEntity)) {
                event.setResult(Event.Result.ALLOW);
            }
        }
    }
}
