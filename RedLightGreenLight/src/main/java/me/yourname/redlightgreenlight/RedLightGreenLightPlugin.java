package me.yourname.redlightgreenlight;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

public class RedLightGreenLightPlugin extends JavaPlugin implements Listener {

    private enum LightState { GREEN, RED }

    private LightState currentState = LightState.GREEN;
    private boolean warning = false;     // 1-second pre-red warning window (movement allowed)
    private boolean running = false;

    private final Random random = new Random();

    // Scheduled tasks for phase switching (so we can cancel and avoid stacking timers)
    private BukkitTask warningTask;
    private BukkitTask switchTask;
    private BukkitTask redTask;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("RedLightGreenLight enabled!");
    }

    @Override
    public void onDisable() {
        stopGame();
        getLogger().info("RedLightGreenLight disabled!");
    }

    private void startGame() {
        if (running) return;
        running = true;
        cancelPhaseTasks();
        startGreenPhase();
    }

    private void stopGame() {
        running = false;
        cancelPhaseTasks();
        currentState = LightState.GREEN;
        warning = false;
    }

    private void resetGame() {
        stopGame();
        Bukkit.broadcastMessage(ChatColor.GRAY + "RLGL reset.");
    }

    private void forceGreen() {
        if (!running) return;
        cancelPhaseTasks();
        startGreenPhase();
    }

    private void forceRed() {
        if (!running) return;
        cancelPhaseTasks();
        switchToRed();
    }

    private void cancelPhaseTasks() {
        if (warningTask != null) {
            warningTask.cancel();
            warningTask = null;
        }
        if (switchTask != null) {
            switchTask.cancel();
            switchTask = null;
        }
        if (redTask != null) {
            redTask.cancel();
            redTask = null;
        }
        warning = false;
    }

    private void startGreenPhase() {
        if (!running) return;

        cancelPhaseTasks();

        currentState = LightState.GREEN;
        warning = false;

        // Random GREEN duration: 1–120 seconds
        int duration = 20 * (random.nextInt(120) + 1);

        broadcastGreen();

        // 1-second warning BEFORE red (only if green is > 1 second)
        if (duration > 20) {
            warningTask = Bukkit.getScheduler().runTaskLater(this, this::sendRedWarning, duration - 20L);
        }

        // IMPORTANT: store this task so it can be cancelled (prevents timer stacking / 3s flip bug)
        switchTask = Bukkit.getScheduler().runTaskLater(this, this::switchToRed, duration);
    }

    private void sendRedWarning() {
        if (!running) return;
        warning = true;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.YELLOW + "⚠ Red Light in 1 second!");
            p.sendTitle(ChatColor.RED + "⚠ WARNING", ChatColor.YELLOW + "Red Light in 1 second!", 10, 20, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        }
    }

    private void switchToRed() {
        if (!running) return;

        // Clear any pending phase timers before switching
        cancelPhaseTasks();

        currentState = LightState.RED;
        warning = false;

        broadcastRed();

        // RED duration fixed: 3 seconds (60 ticks)
        redTask = Bukkit.getScheduler().runTaskLater(this, this::startGreenPhase, 60L);
    }

    private void broadcastGreen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.GREEN + "🟢 GREEN LIGHT! You can move!");
            p.sendTitle(ChatColor.GREEN + "🟢 GREEN LIGHT", ChatColor.GREEN + "Go Go Go!", 10, 40, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        }
    }

    private void broadcastRed() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.RED + "🔴 RED LIGHT! Do NOT move!");
            p.sendTitle(ChatColor.RED + "🔴 RED LIGHT", ChatColor.RED + "Freeze!", 10, 40, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!running) return;
        if (currentState != LightState.RED) return;
        if (warning) return; // allow movement during the 1-second warning window

        Player player = event.getPlayer();

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Allow turning head freely; punish only real movement (block change)
        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {

            player.setHealth(0.0);
        }
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        Entity e = event.getEntity();
        if (e instanceof EnderDragon) {
            stopGame();
            Bukkit.broadcastMessage(ChatColor.GOLD + "🏁 The Ender Dragon has been slain! RLGL game ended!");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("rlgl.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        switch (cmd.getName().toLowerCase()) {
            case "rlglstart":
                startGame();
                sender.sendMessage(ChatColor.GREEN + "RLGL started.");
                return true;

            case "rlglstop":
                stopGame();
                sender.sendMessage(ChatColor.RED + "RLGL stopped.");
                return true;

            case "rlglreset":
                resetGame();
                sender.sendMessage(ChatColor.GRAY + "RLGL reset.");
                return true;

            case "rlglforcegreen":
                forceGreen();
                sender.sendMessage(ChatColor.GREEN + "Forced GREEN.");
                return true;

            case "rlglforcered":
                forceRed();
                sender.sendMessage(ChatColor.RED + "Forced RED.");
                return true;

            default:
                return false;
        }
    }
}
