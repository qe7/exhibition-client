package exhibition.module.impl.other;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import exhibition.Client;
import exhibition.event.Event;
import exhibition.event.RegisterEvent;
import exhibition.event.impl.EventTick;
import exhibition.management.notifications.usernotification.Notifications;
import exhibition.module.Module;
import exhibition.module.data.ModuleData;
import exhibition.module.data.settings.Setting;
import exhibition.util.MathUtils;
import exhibition.util.Timer;
import exhibition.util.security.Connection;
import exhibition.util.security.Connector;
import net.minecraft.util.StringUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BanStats extends Module {

    private BanStatsThread banStatsThread;

    public long banDifference;
    public long bansLastMinute;
    public long bansSinceConnect;


    public final Timer banTimer = new Timer();

    private final ConcurrentHashMap<Long, Long> banDifferenceMap = new ConcurrentHashMap<>();

    public final Setting<Boolean> alertBans = new Setting<>("ALERT", false, "Notifies you if BanStats notices spikes in bans.");

    public BanStats(ModuleData data) {
        super(data);
        addSetting(alertBans);
    }

    @Override
    public void onEnable() {
        banStatsThread = new BanStatsThread(this);
    }

    @Override
    public void onDisable() {
        banStatsThread.stopThread();
    }

    @RegisterEvent(events = {EventTick.class})
    public void onEvent(Event event) {
        if (!banStatsThread.isRunning) {
            banTimer.reset();
            try {
                banStatsThread.start();
            } catch (Exception e) {
                banStatsThread.stopThread();
                banStatsThread = new BanStatsThread(this);
            }
        }

        try {
            long tempBansLastMinute = 0;
            Iterator<Map.Entry<Long, Long>> iterator = banDifferenceMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Long> set = iterator.next();

                if (set.getKey() < System.currentTimeMillis()) {
                    iterator.remove();
                    continue;
                }

                long bans = set.getValue();
                tempBansLastMinute += bans;
            }
            bansLastMinute = tempBansLastMinute;
        } catch (Exception e) {

        }
    }

    private static class BanStatsThread extends Thread {

        private final BanStats banStats;
        private boolean isRunning;
        private long staffTotalBans = 0;

        public BanStatsThread(BanStats banStats) {
            this.banStats = banStats;
            this.isRunning = false;
        }

        @Override
        public synchronized void start() {
            isRunning = true;
            super.start();
        }

        @Override
        public void run() {
            while (Client.instance != null && banStats.isEnabled() && isRunning) {
                while (mc.getIntegratedServer() != null || banStats.mc.thePlayer == null || banStats.mc.theWorld == null || Client.instance.hypixelApiKey == null || Client.instance.hypixelApiKey.equals("")) {
                    if (!isRunning)
                        return;
                    Thread.yield();
                }

                try {
                    Connection hypixelApiConnection = new Connection("https://api.hypixel.net/watchdogStats");

                    hypixelApiConnection.setParameters("key", Client.instance.hypixelApiKey);

                    try {
                        Connector.get(hypixelApiConnection);
                        String response = hypixelApiConnection.getResponse();
                        JsonObject jsonObject = new JsonParser()
                                .parse(response)
                                .getAsJsonObject();

                        boolean success = jsonObject.has("success") && jsonObject.get("success").getAsBoolean();

                        if (success) {
                            long staff_total = jsonObject.get("staff_total").getAsLong();
                            if(staffTotalBans != 0 && banStats.bansSinceConnect == 0 && banStats.banTimer.getDifference() >= 40_000) {
                                staffTotalBans = 0;
                                banStats.banTimer.reset();
                            }

                            long diff = staff_total - staffTotalBans;

                            if (staffTotalBans != 0 && diff >= 4 && banStats.alertBans.getValue()) {
                                Notifications.getManager().post("Staff Activity", "Staff seem to be banning a lot. (+" + diff + ")", 3000, Notifications.Type.WARNING);
                            }

                            if (staffTotalBans != 0 && diff > 0 && banStats.alertBans.getValue() && banStats.banTimer.getDifference() >= 120_000) {

                                long roundedTime = (long) MathUtils.getIncremental(banStats.banTimer.getDifference(), 50);
                                String time = "(" + StringUtils.ticksToElapsedTime((int) roundedTime / 50) + ")";

                                Notifications.getManager().post("Staff Activity", "Staff are now active after " + time + " (+" + diff + ")", 3000, Notifications.Type.WARNING);
                            }

                            if (staffTotalBans != 0)
                                banStats.banDifference = diff;
                            if (staffTotalBans != 0 && diff != 0) {
                                banStats.banTimer.reset();
                                banStats.banDifferenceMap.put(System.currentTimeMillis() + 300_000, diff);
                                banStats.bansSinceConnect += diff;
                            }

                            staffTotalBans = staff_total;
                            Thread.sleep(10_000);
                        } else {
                            Thread.sleep(2_500);
                        }
                    } catch (Exception e) {
                        Thread.sleep(1_000);
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    isRunning = false;
                }
            }
        }

        public void stopThread() {
            this.isRunning = false;
        }

    }

}
