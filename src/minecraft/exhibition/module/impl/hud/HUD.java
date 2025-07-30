package exhibition.module.impl.hud;

import com.github.creeper123123321.viafabric.handler.CommonTransformer;
import com.github.creeper123123321.viafabric.handler.clientside.VRDecodeHandler;
import com.mojang.authlib.GameProfile;
import exhibition.Client;
import exhibition.event.Event;
import exhibition.event.RegisterEvent;
import exhibition.event.impl.EventRenderGui;
import exhibition.event.impl.EventTick;
import exhibition.management.ColorManager;
import exhibition.management.animate.Opacity;
import exhibition.management.font.DynamicTTFFont;
import exhibition.module.Module;
import exhibition.module.data.ModuleData;
import exhibition.module.data.MultiBool;
import exhibition.module.data.Options;
import exhibition.module.data.settings.Setting;
import exhibition.module.impl.other.AutoSkin;
import exhibition.module.impl.other.ChatCommands;
import exhibition.util.HypixelUtil;
import exhibition.util.MathUtils;
import exhibition.util.RenderingUtil;
import exhibition.util.render.Colors;
import io.netty.channel.ChannelHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import us.myles.ViaVersion.api.protocol.ProtocolVersion;
import us.myles.ViaVersion.protocols.base.ProtocolInfo;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class HUD extends Module {

    private final String SPEED = "SPEED";
    private final String COLOR = "COLOR";
    private final MultiBool options;
    private final String UGLYGOD = "CLIENT-NAME";
    private final Options arrayPos = new Options("Array Position", "Top-Right", "Top-Right", "Top-Left");
    private final Opacity hueThing = new Opacity(0);

    private final Setting<Boolean> showUID = new Setting<>("SHOW-UID", false, "Shows your UID instead of your username.");

    private final Setting<Boolean> showPlayerWarning = new Setting<>("WARNING LIST", false);
    private final Setting<Boolean> showSessionTime = new Setting<>("SESSION TIME", false);
    private final Setting<Boolean> showProtocol = new Setting<>("PROTOCOL", true);
    private final Setting<Boolean> drawTicksPerSecond = new Setting<>("TPS", true);

    private final ResourceLocation etb = new ResourceLocation("textures/shit.png"); // 512 x 512

    public HUD(ModuleData data) {
        super(data);
        settings.put(showUID.getName(), showUID);
        settings.put(COLOR, new Setting<>(COLOR, new Options("Color Mode", "White", "Custom", "White", "Rainbow", "Fade"), "Choose the color for the arraylist."));
        settings.put(SPEED, new Setting<>(SPEED, 15, "The speed colors will alternate from.", 1, 1, 10));
        settings.put(UGLYGOD, new Setting<>(UGLYGOD, "Exhibition", "Oh look mom, I can rename a client!"));
        settings.put("ARRAYPOS", new Setting<>("ARRAY", arrayPos, "Array list positioning."));
        Setting[] ents = new Setting[]{
                showPlayerWarning,
                showSessionTime,
                new Setting<>("NOSTALGIA", false),
                new Setting<>("ARRAYLIST", true),
                showProtocol,
                new Setting<>("COORDS", false),
                new Setting<>("SUFFIX", true),
                new Setting<>("TIME", false),
                new Setting<>("PING", false),
                new Setting<>("FPS", false),
                drawTicksPerSecond};
        settings.put("OPTIONS", new Setting<>("OPTIONS", options = new MultiBool("HUD Options", ents), "Extra options you can enable in the HUD."));
        settings.put("RENDERER", new Setting<>("RENDERER", selectedFont, "Select which fontrenderer to use for the overlay."));
    }

    private final Options selectedFont = new Options("Font Renderer", "SmoothTTF", "SmoothTTF", "Minecraft");

    private long lastPinged;
    private int ping;

    public boolean showUID() {
        return showUID.getValue();
    }

    private void checkPing() {
        long l = System.currentTimeMillis();
        if (l - lastPinged < 1000) {
            return;
        }
        lastPinged = l;
        GameProfile gameProfile = mc.thePlayer.getGameProfile();
        final NetHandlerPlayClient var4 = mc.thePlayer.sendQueue;
        List<NetworkPlayerInfo> list = GuiPlayerTabOverlay.playerInfoMap.sortedCopy(var4.getPlayerInfoMap());
        for (NetworkPlayerInfo networkPlayerInfo : list) {
            if (networkPlayerInfo.getGameProfile() != null && gameProfile != null)
                if (gameProfile.equals(networkPlayerInfo.getGameProfile()) || (networkPlayerInfo.getGameProfile() != null && gameProfile.getName().equals(networkPlayerInfo.getGameProfile().getName()))) {
                    ping = Math.max(networkPlayerInfo.getResponseTime(), 0);
                    break;
                }
        }
    }

    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("hh:mm a");

    @Override
    public Priority getPriority() {
        return Priority.HIGHEST;
    }

    private int currentColor;
    private final Opacity fadeState = new Opacity(0);
    private boolean goingUp;

    public DynamicTTFFont.DynamicTTForMC getFont() {
        return Client.hudFont;
    }

    private void updateFade() {
        if (fadeState.getOpacity() >= 255 || fadeState.getOpacity() <= -1) {
            goingUp = !goingUp;
        }

        fadeState.interp(goingUp ? 255 : -1, ((Number) settings.get(SPEED).getValue()).intValue());

        final double ratio = fadeState.getScale();
        int r = ColorManager.hudColor.red;
        int g = ColorManager.hudColor.green;
        int b = ColorManager.hudColor.blue;
        currentColor = getFadeHex(Colors.getColor((int) (r * 0.6), (int) (g * 0.6), (int) (b * 0.6)), Colors.getColor(r, g, b), ratio);
    }

    private int getFadeHex(final int hex1, final int hex2, final double ratio) {
        int r = hex1 >> 16;
        int g = hex1 >> 8 & 0xFF;
        int b = hex1 & 0xFF;
        r += (int) (((hex2 >> 16) - r) * ratio);
        g += (int) (((hex2 >> 8 & 0xFF) - g) * ratio);
        b += (int) (((hex2 & 0xFF) - b) * ratio);
        return r << 16 | g << 8 | b;
    }

    @RegisterEvent(events = {EventRenderGui.class, EventTick.class})
    public void onEvent(Event event) {

        if (event instanceof EventTick) {
            checkPing();
            Client.hudFont.renderMC = selectedFont.getSelected().equalsIgnoreCase("Minecraft");
            return;
        }

//        GlStateManager.pushMatrix();
//        RenderingUtil.rectangle(100, 100, 150, 150, Colors.getColor(15));
//        boolean shift = false;
//        for (double y = 0; y < 50; y += 1.5) {
//            for (double x = (shift ? 1 : 0); x < 50; x += 2) {
//                RenderingUtil.rectangle(100 + x + 0.5, 100 + y, 100 + x + 1, 100 + y + 1.5, Colors.getColor(25));
//                RenderingUtil.rectangle(100 + x, 100 + y + 0.5, 100 + x + 1.5, 100 + y + 1, Colors.getColor(25));
//            }
//            shift = !shift;
//        }
//        RenderingUtil.rectangleBordered(100 - 0.5, 100 - 0.5, 150 + 0.5, 150 + 0.5, 0.5, Colors.getColor(0,0), Colors.getColor(50));
//        RenderingUtil.rectangle(100 - 0.5, 150, 150 + 0.5, 150 + 0.5, ColorManager.hudColor.getColorHex());
//        Client.fss.drawStringWithShadow("Exhibition v2", 101, 101, -1);
//        RenderingUtil.rectangle(100.5, 105.5, 131.5, 106, ColorManager.hudColor.getColorHex());
//
//        int aaeaaa = 0;
//        for (ModuleData.Type value : ModuleData.Type.values()) {
//            Client.fss.drawStringWithShadow(value.name(), 101, 107 + aaeaaa, -1);
//            aaeaaa += 6;
//        }
//        GlStateManager.popMatrix();


//        GlStateManager.pushMatrix();
//        GlStateManager.translate(100,100,0);
//        RenderingUtil.rectangle(0, 0, 256, 256, -1);
//
//
//        boolean bruh = true;
//
//        for(float offsetY = 0; offsetY < 256; offsetY+=4) {
//            for (float xOffset = 0; xOffset < 256; xOffset += 3.5) {
//                if (bruh) {
//                    // Left
//                    RenderingUtil.rectangle(xOffset + 0, offsetY + 0, xOffset + 0.5, offsetY + 0.5, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 0.5, offsetY + 0.5, xOffset + 1, offsetY + 1, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 1, offsetY + 1, xOffset + 1.5, offsetY + 1.5, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 1.5, offsetY + 1.5, xOffset + 2, offsetY + 2, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 2, offsetY + 2, xOffset + 2.5, offsetY + 2.5, Colors.getColor(0));
//
//                    // Right
//                    RenderingUtil.rectangle(xOffset + 2, offsetY + 0, xOffset + 2.5, offsetY + 0.5, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 1.5, offsetY + 0.5, xOffset + 2, offsetY + 1, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 0.5, offsetY + 1.5, xOffset + 1, offsetY + 2, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 0, offsetY + 2, xOffset + 0.5, offsetY + 2.5, Colors.getColor(0));
//                } else {
//                    RenderingUtil.rectangle(xOffset + 0.5, offsetY + 1, xOffset + 1, offsetY + 1.5, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 1.5, offsetY + 1, xOffset + 2, offsetY + 1.5, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 1, offsetY + 0.5, xOffset + 1.5, offsetY + 1, Colors.getColor(0));
//                    RenderingUtil.rectangle(xOffset + 1, offsetY + 1.5, xOffset + 1.5, offsetY + 2, Colors.getColor(0));
//                }
//                bruh = !bruh;
//            }
//            bruh = !bruh;
//        }
//
//        GlStateManager.popMatrix();


        updateFade();
        if (mc.gameSettings.showDebugInfo) {
            return;
        }

        EventRenderGui e = (EventRenderGui) event;

        if (Client.ticksInGame != -1 && showSessionTime.getValue()) {
            long timeDifference = Client.ticksInGame * 50;
            long seconds = (timeDifference / 1000) % 60;
            long minutes = (timeDifference / 60000) % 60;
            long hours = (timeDifference / 3600000) % 24;
            long days = timeDifference / 86400000;
            StringBuilder stringBuilder = new StringBuilder();
            if (days > 0) stringBuilder.append(days).append("d ");
            if (hours > 0) stringBuilder.append(hours).append("h ");
            if (minutes > 0) stringBuilder.append(minutes).append("m ");
            if (seconds >= 0) stringBuilder.append(seconds).append("s");

            String s = stringBuilder.toString();

            mc.fontRendererObj.drawStringWithShadow(s, (int) (e.getResolution().getScaledWidth_double() / 2 - mc.fontRendererObj.getStringWidth(s) / 2), 30, -1);
        }

//        Bypass bypass = Client.getModuleManager().get(Bypass.class);
//        if (GlobalValues.allowDebug.getValue() && bypass.isEnabled() && mc.getIntegratedServer() == null)
//            if (bypass.option.getSelected().equals("Dong")) {
//                int current = Math.max((bypass.bruh - 10), 0);
//                int max = (45 + bypass.randomDelay);
//
//                String bruh = bypass.bruh == 0 ? "Watchdog Inactive" : Math.round((current / (float) max) * 100) + "%";
//                mc.fontRendererObj.drawStringWithShadow(bruh, (int) (e.getResolution().getScaledWidth_double() / 2 - mc.fontRendererObj.getStringWidth(bruh) / 2), 20, -1);
//            } else if (bypass.option.getSelected().equals("Watchdog Off")) {
//                String bruh = bypass.lastSentUid != 2 ? bypass.lastSentUid == 1 ? "Watchdog Off" : "Watchdog Inactive" : "\247c\247lWatchdog Bugged";
//                mc.fontRendererObj.drawStringWithShadow(bruh, (int) (e.getResolution().getScaledWidth_double() / 2 - mc.fontRendererObj.getStringWidth(bruh) / 2), 20, -1);
//            }

//        String okbruh = Angle.INSTANCE.angleVL + " Angle VL | Size: " + Angle.INSTANCE.angleHits.size();
//        mc.fontRendererObj.drawStringWithShadow(okbruh, e.getResolution().getScaledWidth_double() / 2 - 50, 250, -1);
//
//
//        final long time2 = System.currentTimeMillis();
//        double deltaMove = 0D;
//        long deltaTime = 0L;
//        float deltaYaw = 0f;
//        int deltaSwitchTarget = 0;
//
//        final Iterator<Angle.AttackLocation> it = Angle.INSTANCE.angleHits.iterator();
//        while (it.hasNext()) {
//            final Angle.AttackLocation refLoc = it.next();
//            if (time2 - refLoc.time > 1000L) {
//                it.remove();
//                continue;
//            }
//            deltaMove += refLoc.distSqLast;
//            final double yawDiff = Math.abs(refLoc.yawDiffLast);
//            deltaYaw += yawDiff;
//            deltaTime += refLoc.timeDiff;
//            if (refLoc.idDiffLast && yawDiff > 30.0) {
//                deltaSwitchTarget += 1;
//            }
//        }
//
//        // Check if there is enough data present.
//        if (Angle.INSTANCE.angleHits.size() >= 2) {
//            final double n = Angle.INSTANCE.angleHits.size() - 1;
//
//            // Let's calculate the average move.
//            final double averageMove = deltaMove / n;
//
//            // And the average time elapsed.
//            final double averageTime = (double) deltaTime / n;
//
//            // And the average yaw delta.
//            final double averageYaw = (double) deltaYaw / n;
//
//            // Average target switching.
//            final double averageSwitching = (double) deltaSwitchTarget / n;
//
//            mc.fontRendererObj.drawStringWithShadow(String.format("Average Move: %f", averageMove), e.getResolution().getScaledWidth_double() / 2 - 50, 260, (averageMove >= 0.0 && averageMove < 0.2D) ? Colors.getColor(255,150,150) : -1);
//            mc.fontRendererObj.drawStringWithShadow(String.format("Average Time: %f", averageTime), e.getResolution().getScaledWidth_double() / 2 - 50, 270, (averageTime >= 0.0 && averageTime < 150.0) ? Colors.getColor(255,150,150) : -1);
//            mc.fontRendererObj.drawStringWithShadow(String.format("Average Yaw: %f", averageYaw), e.getResolution().getScaledWidth_double() / 2 - 50, 280, (averageYaw > 50.0) ? Colors.getColor(255,150,150) : -1);
//            mc.fontRendererObj.drawStringWithShadow(String.format("Average Switch: %f", averageSwitching), e.getResolution().getScaledWidth_double() / 2 - 50, 290, (averageSwitching > 0.0) ? Colors.getColor(255,150,150) : -1);
//
//        }

//        TTFFontRenderer smallFont = Client.fonts[0];
//        smallFont.drawBorderedString("30/30", e.getResolution().getScaledWidth() / 2D - 15 - (int) smallFont.getWidth("30/30"), e.getResolution().getScaledHeight_double() / 2 - 0.5, -1, Colors.getColor(0, 200));
//        smallFont.drawBorderedString("100HP", e.getResolution().getScaledWidth() / 2D + 15, e.getResolution().getScaledHeight_double() / 2 - 0.5, -1, Colors.getColor(0, 200));
//        smallFont.drawBorderedString("Reloading", e.getResolution().getScaledWidth() / 2D - (int) smallFont.getWidth("Reloading") / 2, e.getResolution().getScaledHeight_double() / 2 + 15, Colors.getColor(91,255,51), Colors.getColor(0, 200));

        String clientName = ((String) settings.get(UGLYGOD).getValue());
        hueThing.interp(255, ((Number) settings.get(SPEED).getValue()).intValue());
        if (hueThing.getOpacity() >= 255) {
            hueThing.setOpacity(0);
        }
        float h = hueThing.getOpacity();
        boolean drawTime = (options.getValue("TIME"));
        boolean fpsTime = (options.getValue("FPS"));
        boolean drawPing = (options.getValue("PING"));
        boolean drawProtocol = showProtocol.getValue();
        boolean drawTPS = drawTicksPerSecond.getValue();
        boolean nostalgia = (options.getValue("NOSTALGIA"));
        boolean suf = (options.getValue("SUFFIX"));
        boolean array = (options.getValue("ARRAYLIST"));
        drawPotionStatus(e.getResolution());

        if (showPlayerWarning.getValue() && HypixelUtil.isInGame("PIT") && !HypixelUtil.scoreboardContains("Event")) {
            NetHandlerPlayClient nethandlerplayclient = this.mc.thePlayer.sendQueue;
            List<NetworkPlayerInfo> list = GuiPlayerTabOverlay.playerInfoMap.<NetworkPlayerInfo>sortedCopy(nethandlerplayclient.getPlayerInfoMap());

            double width = e.getResolution().getScaledWidth();
            double height = e.getResolution().getScaledHeight();

            boolean hasDrawn = false;

            List<String> strings = new ArrayList<>();

            for (NetworkPlayerInfo networkPlayerInfo : list) {
                String displayName = ScorePlayerTeam.formatPlayerName(networkPlayerInfo.getPlayerTeam(), networkPlayerInfo.getGameProfile().getName());
                String name = networkPlayerInfo.getGameProfile().getName();

                if (displayName.equals("\247r" + name) || displayName.equals(name) || displayName.equals("\247r" + name + "\247r") || displayName.equals(name + "\247r")) {
                    continue;
                }

                String[] prefixes = new String[]{"\2472", "\2479", "\247c"};
                for (String prefix : prefixes) {
                    if (displayName.contains(prefix + " " + name)) {
                        if (!hasDrawn) {
                            hasDrawn = true;
                        }
                        strings.add(prefix + name);
                        break;
                    }
                }
            }

            if (!strings.isEmpty()) {
                int offset = 0;
                mc.fontRendererObj.drawStringWithShadow("\247nPossible Staff", width / 2D - mc.fontRendererObj.getStringWidth("\247nPossible Staff") / 2D, 70, -1);
                offset += 12;

                for (String string : strings.stream().sorted().collect(Collectors.toList())) {
                    mc.fontRendererObj.drawStringWithShadow(string, width / 2D - mc.fontRendererObj.getStringWidth(string) / 2D, 70 + offset, -1);
                    offset += 10;
                }
            }

        }

        if (clientName.equals("Virtue 6")) {
            RenderingUtil.rectangleBordered(2.0D, 2.0D, 60.0D, 34.0D, 1.0D, -1603704471, -16777216);
            Client.blockyFont.drawStringWithShadow("Virtue 6", (29 - Client.blockyFont.getStringWidth("Virtue 6") / 2D + 2), 4.0D,
                    -4210753, 0.8F);

            double movementSpeed = Math.sqrt(Math.pow(mc.thePlayer.posX - mc.thePlayer.lastTickPosX, 2) + Math.pow(mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ, 2)) * 20;
            if (mc.timer.timerSpeed > 1) {
                movementSpeed *= mc.timer.timerSpeed;
            }
            String speedStr = String.format("%.2f", movementSpeed);

            String serverVersion = speedStr + " | " + Client.getTPS();

            Client.blockyFont.drawStringWithShadow("Fps: " + Minecraft.getDebugFPS(), (29 - Client.blockyFont.getStringWidth("Fps: " + Minecraft.getDebugFPS()) / 2D + 2), 14.0D, -6513508, 1.2F);
            Client.blockyFont.drawStringWithShadow(serverVersion, (29 - Client.blockyFont.getStringWidth(serverVersion) / 2D + 2), 24.0D, -6513508, 1.2F);
            List<Module> moduleList = Arrays.stream(Client.getModuleManager().getArray()).sorted((m1, m2) -> {
                String s1 = getRenderName(m1);
                String s2 = getRenderName(m2);
                return Client.blockyFont.getStringWidth(s2) - Client.blockyFont.getStringWidth(s1);
            }).collect(Collectors.toList());
            int y = 2;
            for (Module module : moduleList.stream().filter(module -> (!shouldHide(module) && module.isEnabled())).collect(Collectors.toList())) {
                String toRender = getRenderName(module);
                Client.blockyFont.drawStringWithShadow(toRender, (e.getResolution().getScaledWidth() - Client.blockyFont.getStringWidth(toRender) - 2), y, 13356753);
                y += 10;
            }
            return;
        }

        DynamicTTFFont.DynamicTTForMC font = Client.hudFont;

        if (isETB() || isVirtue()) {
            int colorInt = isETB() ? 0xffff4d4c : Colors.getColor(203, 206, 209);
            String hudName = isETB() ? "\24772.0\247r " : "Virtue";
            if (isETB() && mc.thePlayer != null) {
                float yaw = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw + (45F / 2F)) % 360;

                String facing = "N";
                if (yaw >= 0 && yaw < 45)
                    facing = "S";
                if (yaw > 45 && yaw < 90)
                    facing = "SW";
                if (yaw >= 90 && yaw < 135)
                    facing = "W";
                if (yaw > 135 && yaw < 180)
                    facing = "NW";
                if (yaw >= -180 && yaw < -135)
                    facing = "N";
                if (yaw > -135 && yaw < -90)
                    facing = "NE";
                if (yaw >= -90 && yaw < -45)
                    facing = "E";
                if (yaw > -45 && yaw < 0)
                    facing = "SE";
                hudName += "[" + facing + "]";

                mc.getTextureManager().bindTexture(etb);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

                GlStateManager.pushMatrix();
                GlStateManager.enableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.color(1, 1, 1, 1);
                RenderingUtil.drawIcon(1, 2, 0, 0, 24, 24, 24, 24);
                GlStateManager.disableBlend();
                GlStateManager.disableAlpha();
                GlStateManager.popMatrix();
            }
            if (isETB()) {
                mc.fontRendererObj.drawStringWithShadow(hudName, 23, 17, colorInt);
            } else {
                Client.virtueFont.drawStringWithShadow("\247fVirtue" + " \2477(" + getTime() + ")", 2, 2, colorInt);
            }
            if (array) {
                if (isVirtue()) {
                    List<Module> mods = Arrays.stream(Client.getModuleManager().getArray()).filter(Module::isEnabled).filter(m -> !shouldHide(m)).sorted(Comparator.comparingDouble(o -> -Client.virtueFont.getStringWidth(o.getSuffix() != null ? o.getName() + "[" + o.getSuffix() + "]" : o.getName()))).collect(Collectors.toList());
                    int y = 2;
                    for (Module module : mods) {
                        if (!module.isEnabled())
                            continue;
                        int color = -1;
                        switch (module.getType().name()) {
                            case "Combat":
                                color = -4042164;
                                break;
                            case "Other":
                                color = -13911383;
                                break;
                            case "Visuals":
                                color = -1781619;
                                break;
                            case "Movement":
                                color = -4927508;
                                break;
                            case "Player":
                                color = -8921737;
                                break;
                        }
                        String name = module.getName() + (module.getSuffix() == null ? "" : " \2477[" + module.getSuffix() + "]");
                        float width = Client.virtueFont.getStringWidth(name);
                        Client.virtueFont.drawStringWithShadow(name, e.getResolution().getScaledWidth() - width - 2, y, color);
                        y += 10;
                    }
                } else {
                    int y = 1;
                    List<Module> moduleList = Arrays.stream(Client.getModuleManager().getArray()).filter(module -> (!shouldHide(module) && module.isEnabled())).sorted(Comparator.comparingDouble(o -> -mc.fontRendererObj.getStringWidth(o.getSuffix() != null ? o.getName() + " - " + o.getSuffix() : o.getName()))).collect(Collectors.toList());
                    for (Module module : moduleList) {
                        if (h > 255) {
                            h -= 255;
                        }
                        String name = module.getName() + (module.getSuffix() == null ? "" : " \2477- " + module.getSuffix());
                        float width = mc.fontRendererObj.getStringWidth(name);
                        int color = MathHelper.hsvToRGB((h / 255.0f) % 1, 0.55f, 0.9f);
                        mc.fontRendererObj.drawStringWithShadow(name, e.getResolution().getScaledWidth() - width - 1, y, color);
                        y += 10;
                        h += 9;
                    }
                }
            }

            int yOffset = isETB() ? 16 : 0;

            if (isVirtue()) {
                RenderingUtil.rectangle(2, yOffset + 12, 55, yOffset + 15 + 56 + 12, -1610612736);
                RenderingUtil.rectangle(3, yOffset + 13, 54, yOffset + 13 + 14, colorInt);

                Client.virtueFont.drawStringWithShadow("Combat", 29 - Client.virtueFont.getStringWidth("Combat") / 2F, yOffset + 16, -1);
                Client.virtueFont.drawString("\2477Render", 29 - Client.virtueFont.getStringWidth("Render") / 2F, yOffset + 16 + 14, -1);
                Client.virtueFont.drawString("\2477Movement", 29 - Client.virtueFont.getStringWidth("Movement") / 2F, yOffset + 16 + 28, -1);
                Client.virtueFont.drawString("\2477Player", 29 - Client.virtueFont.getStringWidth("Player") / 2F, yOffset + 16 + 42, -1);
                Client.virtueFont.drawString("\2477World", 29 - Client.virtueFont.getStringWidth("World") / 2F, yOffset + 16 + 56, -1);
                int bruhOffsetY = 0;
                if (drawProtocol) {
                    Client.virtueFont.drawStringWithShadow("\2477Ver: \247f" + getServerProtocol(), 3, yOffset + 85, -1);
                    bruhOffsetY += 10;
                }
                if (fpsTime) {
                    Client.virtueFont.drawStringWithShadow("\2477FPS: \247f" + Minecraft.getDebugFPS(), 3, yOffset + 85 + bruhOffsetY, -1);
                    bruhOffsetY += 10;
                }
                if (drawPing) {
                    Client.virtueFont.drawStringWithShadow("\2477Ping: \247f" + ping, 3, yOffset + 85 + bruhOffsetY, -1);
                    bruhOffsetY += 10;
                }
                if (drawTPS) {
                    Client.virtueFont.drawStringWithShadow("\2477TPS: \247f" + Client.getTPS(), 3, yOffset + 85 + bruhOffsetY, -1);
                    bruhOffsetY += 10;
                }
            } else {
                RenderingUtil.rectangleBordered(2, yOffset + 12, 62, yOffset + 74, 1, Colors.getColor(0, 150), Colors.getColor(0, 200));
                RenderingUtil.rectangle(3, yOffset + 13, 61, yOffset + 25, colorInt);

                mc.fontRendererObj.drawStringWithShadow("Combat", 8, yOffset + 15, -1);
                mc.fontRendererObj.drawString("\2477Render", 6, yOffset + 27, -1);
                mc.fontRendererObj.drawString("\2477Movement", 6, yOffset + 39, -1);
                mc.fontRendererObj.drawString("\2477Player", 6, yOffset + 51, -1);
                mc.fontRendererObj.drawString("\2477World", 6, yOffset + 63, -1);

                int bruhOffsetY = 0;
                if (drawProtocol) {
                    mc.fontRendererObj.drawStringWithShadow("\2477Ver: \247f" + getServerProtocol(), 3, yOffset + 77, -1);
                    bruhOffsetY += 10;
                }
                if (fpsTime) {
                    mc.fontRendererObj.drawStringWithShadow("\2477FPS: \247f" + Minecraft.getDebugFPS(), 2, yOffset + 77 + bruhOffsetY, -1);
                    bruhOffsetY += 10;
                }
                if (drawPing) {
                    mc.fontRendererObj.drawStringWithShadow("\2477Ping: \247f" + ping, 2, yOffset + 77 + bruhOffsetY, -1);
                    bruhOffsetY += 10;
                }
                if (drawTPS) {
                    mc.fontRendererObj.drawStringWithShadow("\2477TPS: \247f" + Client.getTPS(), 2, yOffset + 77 + bruhOffsetY, -1);
                    bruhOffsetY += 10;
                }
            }

            return;
        }

        String selected = ((Options) settings.get(COLOR).getValue()).getSelected();
        int c2222 = Colors.getColorOpacity(MathHelper.hsvToRGB((h / 255.0f) % 1, 0.55f, 0.9f), 255);
        int colorXD = selected.equalsIgnoreCase("Rainbow") ? c2222 : selected.equalsIgnoreCase("Fade") ? currentColor : Colors.getColor(ColorManager.hudColor.red, ColorManager.hudColor.green, ColorManager.hudColor.blue, 220);
        if (!nostalgia) {
            if (clientName.equalsIgnoreCase("") || clientName.toLowerCase().equalsIgnoreCase("Astolfo")) {
                clientName = "Exhibition";
            }
            String prefix = (clientName.toLowerCase().startsWith("novoline") || clientName.toLowerCase().startsWith("astolfo") ? "" : "\247l") + clientName.charAt(0);

            String ok = new ChatComponentText(clientName.substring(1).replace("&", "\247")).getFormattedText();
            Date now = new Date();
            String strDate = simpleDateFormat.format(now);
            if (drawProtocol)
                ok += " \2477[\247r" + (font.renderMC ? "" : "\247l") + getServerProtocol() + "\2477]\247f";
            if (drawTime)
                ok += " \2477[\247r" + (font.renderMC ? "" : "\247l") + strDate + "\2477]\247f";
            if (fpsTime)
                ok += " \2477[\247r" + (font.renderMC ? "" : "\247l") + Minecraft.getDebugFPS() + " FPS\2477]\247f";
            if (drawPing)
                ok += " \2477[\247r" + (font.renderMC ? "" : "\247l") + ping + "ms\2477]\247f";
            if (drawTPS)
                ok += " \2477[\247r" + (font.renderMC ? "" : "\247l") + Client.getTPS() + "\2477]\247f";

            GlStateManager.pushMatrix();
            GlStateManager.translate(2, 2, 0);
            font.drawStringWithShadow(prefix, 0, 0, colorXD);
            float weirdFix = clientName.length() >= 2 ? font.getWidth(prefix + "\247r" + clientName.charAt(1)) - font.getWidth(prefix) - font.getWidth(clientName.substring(1, 2)) : 0;
            font.drawStringWithShadow((font.renderMC ? "" : "\247l") + ok, font.getWidth(prefix) + weirdFix, 0, Colors.getColor(255, 220));

            GlStateManager.popMatrix();

            boolean left = arrayPos.getSelected().equalsIgnoreCase("top-left");
            int y = left ? Client.getModuleManager().isEnabled(TabGUI.class) ? 76 : 12 : 1;
            if (array) {
                List<Module> modules = new ArrayList<>();
                for (Module module : Client.getModuleManager().getArray()) {
                    if (module.isEnabled() || module.translate.getX() != -50)
                        modules.add(module);
                    if (!module.isEnabled() || shouldHide(module))
                        module.translate.interpolate(left ? -50 : e.getResolution().getScaledWidth(), -20, 0.6F);
                }

                modules.sort(Comparator.comparingDouble(o -> -MathUtils.getIncremental(font.getWidth(suf && o.getSuffix() != null ? o.getName() + " " + o.getSuffix() : o.getName()), 0.5)));

                for (Module module : modules) {
                    if (h > 255) {
                        h -= 255;
                    }
                    String suffix = suf && module.getSuffix() != null ? " \2477" + module.getSuffix() : "";
                    float x = left ? 2 : e.getResolution().getScaledWidth() - font.getWidth(module.getName() + suffix) - 1;
                    if (module.isEnabled() && !shouldHide(module))
                        module.translate.interpolate(x, y, 0.35F);

                    int c = Colors.getColorOpacity(MathHelper.hsvToRGB((h / 255.0f) % 1, 0.55f, 0.9f), 255);
                    boolean rainbow = selected.equalsIgnoreCase("Rainbow");
                    boolean cus = selected.equalsIgnoreCase("Custom");
                    boolean fade = selected.equalsIgnoreCase("Fade");

                    if (fade) {
                        double ratio = ((mc.thePlayer.ticksExisted * (10 * ((Number) settings.get(SPEED).getValue()).intValue()) + y * 7) / 255.0D) % 2;
                        if (ratio > 1) {
                            ratio = 1 - (ratio - 1);
                        }
                        int r = ColorManager.hudColor.red;
                        int g = ColorManager.hudColor.green;
                        int b = ColorManager.hudColor.blue;
                        c = getFadeHex(Colors.getColor((int) (r * 0.6), (int) (g * 0.6), (int) (b * 0.6)), Colors.getColor(r, g, b), ratio);
                    }

                    font.drawStringWithShadow((font.renderMC ? "" : "\247l") + module.getName() + suffix, module.translate.getX(), module.translate.getY(), fade ? c : rainbow ? c : (cus ? ColorManager.hudColor.getColorHex() : Colors.getColor(255, 220)));
                    if (module.isEnabled() && !shouldHide(module)) {
                        h += 9;
                        y += 9;
                    }
                }
            }
        } else {
            Date now = new Date();
            String strDate = simpleDateFormat.format(now);

            if (clientName.equalsIgnoreCase("") || clientName.equalsIgnoreCase("Exhibition")) {
                clientName = "Exhibition";
            }
            String ok = "\2477" + clientName;
            if (drawProtocol)
                ok += " \2477[\247f" + getServerProtocol() + "\2477]\247f";
            if (drawTime)
                ok += " \2477[\247f" + strDate + "\2477]\247f";
            if (fpsTime)
                ok += " \2477[\247f" + Minecraft.getDebugFPS() + " FPS\2477]\247f";
            if (drawPing)
                ok += " \2477[\247f" + ping + "ms\2477]\247f";
            if (drawTPS)
                ok += " \2477[\247f" + Client.getTPS() + "\2477]\247f";

            mc.fontRendererObj.drawStringWithShadow(ok, 2, 2, c2222);
            mc.fontRendererObj.drawStringWithShadow(clientName.substring(0, 1), 2, 2, 0xbcffbc);

            boolean left = arrayPos.getSelected().equalsIgnoreCase("top-left");
            int y = left ? Client.getModuleManager().isEnabled(TabGUI.class) ? 76 : 12 : 1;
            if (array) {
                List<Module> modules = new ArrayList<>();
                for (Module module : Client.getModuleManager().getArray()) {
                    if (module.isEnabled() || module.translate.getX() != -50)
                        modules.add(module);
                    if (!module.isEnabled() || shouldHide(module))
                        module.translate.interpolate(left ? -50 : e.getResolution().getScaledWidth(), -20, 0.6F);
                }

                modules.sort(Comparator.comparingDouble(o -> -mc.fontRendererObj.getStringWidth(suf && o.getSuffix() != null ? o.getName() + " " + o.getSuffix() : o.getName())));

                for (Module module : modules) {
                    if (h > 255) {
                        h = 0;
                    }
                    String suffix = suf && module.getSuffix() != null ? " \2477" + module.getSuffix() : "";
                    float x = left ? 2 : e.getResolution().getScaledWidth() - mc.fontRendererObj.getStringWidth(module.getName() + suffix) - 1;
                    if (module.isEnabled() && !shouldHide(module))
                        module.translate.interpolate(x, y, 0.35F);

                    int color = -1;
                    switch (module.getType().name()) {
                        case "Combat":
                            color = 0xdb78a3;
                            break;
                        case "Visuals":
                            color = 0xffbb91;
                            break;
                        case "Player":
                            color = 0xe0c5f2;
                            break;
                        case "Movement":
                            color = 0x5b99cc;
                            break;
                        case "Other":
                            color = 0xc4e0f9;
                            break;
                    }

                    mc.fontRendererObj.drawStringWithShadow(module.getName() + suffix, module.translate.getX(), module.translate.getY(), color);

                    if (module.isEnabled() && !shouldHide(module)) {
                        h += 9;
                        y += 9;
                    }
                }
            }
        }

        if (options.getValue("COORDS")) {
            boolean chat = mc.currentScreen instanceof GuiChat;
            String str = String.format((nostalgia ? "\2477XYZ:  \247r" : (font.renderMC ? "" : "\247l")) + "%d %d %d", (int) mc.thePlayer.posX, (int) mc.thePlayer.posY, (int) mc.thePlayer.posZ);
            double movementSpeed = Math.sqrt(Math.pow(mc.thePlayer.posX - mc.thePlayer.lastTickPosX, 2) + Math.pow(mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ, 2)) * 20;
            if (mc.timer.timerSpeed > 1) {
                movementSpeed *= mc.timer.timerSpeed;
            }
            String speedStr = String.format((nostalgia ? "\2477b/s:  \247r" : (font.renderMC ? "" : "\247l")) + "%.2f", movementSpeed);

            if (nostalgia) {
                mc.fontRendererObj.drawStringWithShadow(str, e.getResolution().getScaledWidth() / 2D - mc.fontRendererObj.getStringWidth(str) / 2D, BossStatus.bossName != null && BossStatus.statusBarTime > 0 ? 20 : 2, colorXD);
                mc.fontRendererObj.drawStringWithShadow(speedStr, e.getResolution().getScaledWidth() / 2D - mc.fontRendererObj.getStringWidth(speedStr) / 2D, BossStatus.bossName != null && BossStatus.statusBarTime > 0 ? 30 : 12, colorXD);
            } else {
                String firstPart = "\2477" + (font.renderMC ? "" : "\247l") + "XYZ: ";
                String firstPart2 = "\2477" + (font.renderMC ? "" : "\247l") + "b/s: ";

                float totalWidth = font.getWidth(firstPart);
                float totalWidth2 = font.getWidth(firstPart2);

                // font.drawStringWithShadow(firstPart + str + " " + firstPart2 + speed, 2, e.getResolution().getScaledHeight() - (chat ? 22 : 12), colorXD);


                font.drawStringWithShadow(firstPart, 2, e.getResolution().getScaledHeight() - (chat ? 25 : 12), colorXD);
                font.drawStringWithShadow(str, 2 + totalWidth, e.getResolution().getScaledHeight() - (chat ? 25 : 12), colorXD);

                double offset = font.getWidth(firstPart + str) + 5;

                font.drawStringWithShadow(firstPart2, 2 + offset, e.getResolution().getScaledHeight() - (chat ? 25 : 12), colorXD);
                font.drawStringWithShadow(speedStr, 2 + offset + totalWidth2, e.getResolution().getScaledHeight() - (chat ? 25 : 12), colorXD);
            }

        }
    }

    private String getServerProtocol() {
        String serverProtocol = "1.8.x";

        if (mc.getNetHandler() != null && mc.getNetHandler().getNetworkManager() != null) {
            ChannelHandler viaDecoder = mc.getNetHandler().getNetworkManager().channel.pipeline().get(CommonTransformer.HANDLER_DECODER_NAME);
            if (viaDecoder instanceof VRDecodeHandler) {
                ProtocolInfo protocol = ((VRDecodeHandler) viaDecoder).getInfo().getProtocolInfo();
                if (protocol != null) {
                    ProtocolVersion serverVer = ProtocolVersion.getProtocol(protocol.getServerProtocolVersion());
                    serverProtocol = serverVer.getName();
                }
            }
        }
        return serverProtocol;
    }

    public int getColor() {
        String selected = ((Options) settings.get(COLOR).getValue()).getSelected();
        int c2222 = Colors.getColorOpacity(MathHelper.hsvToRGB((hueThing.getOpacity() / 255.0f) % 1, 0.55f, 0.9f), 255);
        return selected.equalsIgnoreCase("Rainbow") ? c2222 : selected.equalsIgnoreCase("Fade") ? currentColor : Colors.getColor(ColorManager.hudColor.red, ColorManager.hudColor.green, ColorManager.hudColor.blue, 220);
    }

    private String getRenderName(Module module) {
        return String.format("%s" + ((module.getSuffix() != null && !module.getSuffix().equals("")) ? " \2477(%s)" : ""), module.getName(), module.getSuffix());
    }

    private String getTime() {
        String time = simpleDateFormat.format(new Date());
        if (time.startsWith("0"))
            time = time.replaceFirst("0", "");
        return time;
    }

    public boolean isETB() {
        String clientName = ((String) settings.get(UGLYGOD).getValue());
        return (clientName.equals("ETBISTRASH"));
    }

    private final boolean isVirtue = Boolean.parseBoolean(System.getProperty("virtueTheme2"));

    public boolean isVirtue() {
        String clientName = ((String) settings.get(UGLYGOD).getValue());
        return (clientName.equals("DREAMTRAPGAY") || clientName.equals("Virtue 6"));
    }

    private void drawPotionStatus(ScaledResolution sr) {
        List<PotionEffect> potions = new ArrayList<>();
        for (Object o : mc.thePlayer.getActivePotionEffects())
            potions.add((PotionEffect) o);
        potions.sort(Comparator.comparingDouble(effect -> -Client.hudFont.getWidth(I18n.format((Potion.potionTypes[effect.getPotionID()]).getName()))));
        float pY = (mc.currentScreen instanceof GuiChat) ? -(16 + Client.hudFont.getHeight("SpEgYy") + 3) : -(Client.hudFont.getHeight("SpEgYy") + 3);
        for (PotionEffect effect : potions) {
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            String name = I18n.format(potion.getName());
            String PType = "";
            if (effect.getAmplifier() == 1) {
                name = name + " II";
            } else if (effect.getAmplifier() == 2) {
                name = name + " III";
            } else if (effect.getAmplifier() == 3) {
                name = name + " IV";
            }
            if ((effect.getDuration() < 600) && (effect.getDuration() > 300)) {
                PType = PType + "\2476 " + Potion.getDurationString(effect);
            } else if (effect.getDuration() < 300) {
                PType = PType + "\247c " + Potion.getDurationString(effect);
            } else if (effect.getDuration() > 600) {
                PType = PType + "\2477 " + Potion.getDurationString(effect);
            }
            Color c = new Color(potion.getLiquidColor());
            Client.hudFont.drawStringWithShadow(name,
                    sr.getScaledWidth() - Client.hudFont.getWidth(name + PType) - 1,
                    sr.getScaledHeight() - 9 + pY, Colors.getColor(c.getRed(), c.getGreen(), c.getBlue()));
            Client.hudFont.drawStringWithShadow(PType,
                    sr.getScaledWidth() - Client.hudFont.getWidth(PType) - 1,
                    sr.getScaledHeight() - 9 + pY, -1);
            pY -= 9;
        }
    }

    private boolean shouldHide(Module module) {
        return isBlacklisted(module.getClass()) || module.isHidden();
    }

    private boolean isBlacklisted(Class<? extends Module> clazz) {
        return clazz.equals(ChatCommands.class) || clazz.equals(TabGUI.class) || clazz.equals(BubbleGui.class) || clazz.equals(HUD.class) || clazz.equals(AutoSkin.class);
    }

}
