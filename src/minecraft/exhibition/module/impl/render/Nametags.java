package exhibition.module.impl.render;

import exhibition.Client;
import exhibition.event.Event;
import exhibition.event.RegisterEvent;
import exhibition.event.impl.EventNametagRender;
import exhibition.event.impl.EventRender3D;
import exhibition.event.impl.EventRenderGui;
import exhibition.event.impl.EventTick;
import exhibition.management.PriorityManager;
import exhibition.management.UUIDResolver;
import exhibition.management.font.TTFFontRenderer;
import exhibition.management.friend.FriendManager;
import exhibition.module.Module;
import exhibition.module.data.ModuleData;
import exhibition.module.data.Options;
import exhibition.module.data.settings.Setting;
import exhibition.module.impl.combat.AntiBot;
import exhibition.util.HypixelUtil;
import exhibition.util.MathUtils;
import exhibition.util.RenderingUtil;
import exhibition.util.TeamUtils;
import exhibition.util.render.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StringUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.*;

public class Nametags extends Module {

    public List<EntityPlayer> playerEntities = new ArrayList<>();
    public Map<EntityPlayer, Bruh> entityPositions = new HashMap<>();
    private String INVISIBLES = "INVISIBLES";
    private String OPACITY = "OPACITY";
    private String BOTS = "BOTS";
    private Options armor = new Options("Show Armor", "Hover", "Never", "Hover", "Always", "Priority Only");
    private Options health = new Options("Show Health", "Hover", "Never", "Hover", "Always", "Priority Only");
    private Setting distance = new Setting<>("DISTANCE", false, "Show distance before name.");
    private Setting<Boolean> IGNORESPAWN = new Setting<>("PIT-MODE", true, "Does not render on players in The Pit Spawn.");
    private Setting<Boolean> priorityOnly = new Setting<>("PRIORITY-ONLY", false, "Only renders players who are priority.");

    public Nametags(ModuleData data) {
        super(data);
        addSetting(IGNORESPAWN);
        settings.put("ARMOR", new Setting<>("ARMOR", armor, "Render method for armor."));
        settings.put("HEALTH", new Setting<>("HEALTH", health, "Render method for health."));

        settings.put(INVISIBLES, new Setting<>(INVISIBLES, false, "Show invisibles."));
        settings.put(OPACITY, new Setting<>(OPACITY, false, "Lowers in opacity the farther away from screen center."));
        settings.put("DISTANCE", distance);
        settings.put(BOTS, new Setting<>(BOTS, true, "Informs you if an entity is considered a bot by AntiBot."));
        addSetting(priorityOnly);
    }

    @Override
    public Priority getPriority() {
        return Priority.MEDIUM;
    }

    private final Bruh defaultTo = new Bruh(new double[]{-1337, -1337, -1337, -1337});

    @RegisterEvent(events = {EventRender3D.class, EventRenderGui.class, EventNametagRender.class, EventTick.class})
    public void onEvent(Event event) {
        if (event instanceof EventTick) {
            playerEntities.clear();
            for (Entity entity : mc.theWorld.getLoadedEntityList()) {
                if (entity instanceof EntityPlayer) {
                    this.playerEntities.add((EntityPlayer) entity);
                }
            }
        }
        if (event instanceof EventNametagRender) {
            EventNametagRender er = event.cast();
            if (er.getEntity() instanceof EntityPlayer) {
                EntityPlayer ent = (EntityPlayer) er.getEntity();

                if (entityPositions.containsKey(ent)) {
                    event.setCancelled(true);
                    return;
                }

                boolean ignorePit = HypixelUtil.isInGame("THE HYPIXEL PIT") && IGNORESPAWN.getValue();

                boolean isImportant = TargetESP.isPriority(ent) || FriendManager.isFriend(ent.getName()) || UUIDResolver.instance.isInvalidName(ent.getName());

                if (ignorePit && !isImportant) {
                    double x = ent.posX;
                    double y = ent.posY;
                    double z = ent.posZ;
                    if (y > Client.instance.spawnY && x < 30 && x > -30 && z < 30 && z > -30) {
                        return;
                    }
                }

                if (priorityOnly.getValue() && !isImportant) {
                    return;
                }

                event.setCancelled(true);
            }
        }
        if (event instanceof EventRender3D) {
            try {
                updatePositions();
            } catch (Exception ignored) {

            }
        }
        if (event instanceof EventRenderGui) {
            EventRenderGui er = (EventRenderGui) event;
            ScaledResolution scaledRes = new ScaledResolution(mc);

            TTFFontRenderer font = Client.fonts[3];

            for (EntityPlayer ent : entityPositions.keySet()) {
                double[] renderPositions = entityPositions.getOrDefault(ent, defaultTo).array;

                if ((renderPositions[2] < 0.0D) || (renderPositions[2] >= 1.0D)) {
                    continue;
                }

                boolean prioritized = PriorityManager.isPriority(ent);

                boolean isTeam = TeamUtils.isTeam(mc.thePlayer, ent);

                boolean isPriority = prioritized || TargetESP.isPriority(ent) || FriendManager.isFriend(ent.getName());

                boolean invalid = false;
                if (!isPriority) {
                    if (UUIDResolver.instance.isInvalidName(ent.getName())) {
                        invalid = true;
                        isPriority = true;
                    }
                }

                GlStateManager.pushMatrix();

                GlStateManager.translate(renderPositions[0] / scaledRes.getScaleFactor(), renderPositions[1] / scaledRes.getScaleFactor(), 0.0D);
                scale();

                double ex = ent.lastTickPosX + (ent.posX - ent.lastTickPosX) * mc.timer.renderPartialTicks;
                double ey = ent.lastTickPosY + (ent.posY - ent.lastTickPosY) * mc.timer.renderPartialTicks;
                double ez = ent.lastTickPosZ + (ent.posZ - ent.lastTickPosZ) * mc.timer.renderPartialTicks;

                double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * mc.timer.renderPartialTicks;
                double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * mc.timer.renderPartialTicks;
                double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * mc.timer.renderPartialTicks;

                double d0 = px - ex;
                double d1 = py - ey;
                double d2 = pz - ez;

                double distance = MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
                if (distance <= 4) {
                    double scale = Math.max(Math.min(((4 - distance) / 2.75), 1), 0);
                    GlStateManager.scale(1 + (1.25 * scale), 1 + (1.25 * scale), 1 + (1.25 * scale));
                } else {
                    double centerX = (er.getResolution().getScaledWidth() / 2D);
                    double centerY = (er.getResolution().getScaledHeight() / 2D) + 11;

                    double translateX = renderPositions[0] / scaledRes.getScaleFactor();
                    double translateY = renderPositions[1] / scaledRes.getScaleFactor();

                    double diffX = Math.abs(centerX - translateX);
                    double diffY = Math.abs(centerY - translateY);

                    double mouseDist = Math.hypot(diffX, diffY);

                    if (mouseDist < 20) {
                        double percentage = (20 - mouseDist) / 15D;
                        percentage = Math.max(Math.min(percentage, 1), 0);
                        GlStateManager.scale(1 + (0.1 * percentage), 1 + (0.1 * percentage), 1 + (0.1 * percentage));
                    }
                }

                GlStateManager.translate(0.0D, -2.5D, 0.0D);
                int mouseY = (er.getResolution().getScaledHeight() / 2);
                int mouseX = (er.getResolution().getScaledWidth() / 2);
                double translateX = renderPositions[0] / scaledRes.getScaleFactor();
                double translateY = renderPositions[1] / scaledRes.getScaleFactor();
                float percentage = (float) (1F - (((float) Math.abs(mouseX - translateX) + Math.abs(mouseY - translateY)) / (mouseX + mouseY)) * 3F);
                if (percentage < 0.2) {
                    percentage = 0.2F;
                }

                boolean isBot = AntiBot.isBot(ent);

                if (!(boolean) settings.get(OPACITY).getValue() || isPriority || isBot) {
                    percentage = 1;
                }
                int backgroundColor = FriendManager.isFriend(ent.getName()) ? Colors.getColor(52, 229, 235, 200) :
                        prioritized ? Colors.getColor(255, 0, 0, 200) :
                                Colors.getColor(35, (int) (200 * percentage));
                int borderColor = FriendManager.isFriend(ent.getName()) || isTeam ? Colors.getColor(52, 98, 235) :
                        prioritized ? Colors.getColor(255) :
                                isPriority ? Colors.getColor(255, 178, 0, 200) :
                                        Colors.getColor(28, (int) (200 * percentage));

                if (invalid) {
                    backgroundColor = Colors.getColor(55, 55, 0, (int) (200 * percentage));
                    borderColor = Colors.getColor(255, 255, 0, (int) (200 * percentage));
                }

                String playerName = ent.getDisplayName().getFormattedText().replaceAll("[^\247^\\x00-\\x7F]", "?");

                String str = ((boolean) this.distance.getValue() ? "\247a" + (int) mc.thePlayer.getDistanceToEntity(ent) + "m\247r " : "") + playerName;
                str = str.replace(playerName, FriendManager.isFriend(ent.getName()) ? FriendManager.getAlias(ent.getName()) : "\247f\247l" + playerName);

                if ((boolean) settings.get(BOTS).getValue() && isBot) {
                    boolean isNCP = str.contains("[NPC] ");
                    str = str.replace("[NPC] ", "");
                    str += " \247c[" + (isNCP ? "NPC" : "BOT") + "]";
                }

                str = str.replace("\2478", "\247f");

                float strWidth = font.getWidth(str);

                RenderingUtil.rectangleBordered(-strWidth / 2 - 2, -11, strWidth / 2 + 2, 0, 0.5, backgroundColor, borderColor);
                int x3 = ((int) (renderPositions[0] + -strWidth / 2 - 3) / 2) - 26;
                int x4 = ((int) (renderPositions[0] + strWidth / 2 + 3) / 2) + 20;
                int y1 = ((int) (renderPositions[1] + -30) / 2);
                int y2 = ((int) (renderPositions[1] + 11) / 2);

                int color = Colors.getColor(255, (int) (255 * percentage));

                font.drawBorderedString(str, -strWidth / 2, -8.5F, color, Colors.getColorOpacity(-1, (int) (190 * percentage)));

                String selectHealth = this.health.getSelected();
                String selectArmor = this.armor.getSelected();

                boolean hovered = x3 < mouseX && mouseX < x4 && y1 < mouseY && mouseY < y2;

                float health = Float.isNaN(ent.getHealth()) ? 0 : ent.getHealth();
                float realHealthProgress = (health / ent.getMaxHealth());
                float[] fractions = new float[]{0f, 0.5f, 1f};
                Color[] colors = new Color[]{Color.RED, Color.YELLOW, Color.GREEN};
                Color customColor = health >= 0 ? ESP2D.blendColors(fractions, colors, realHealthProgress).brighter() : Color.RED;

                if (!isBot) {
                    double left = -strWidth / 2 - 2;

                    float progress;
                    float absorption = ent.getAbsorptionAmount();

                    progress = health / (ent.getMaxHealth() + absorption);


                    double difference = strWidth + 4;
                    double healthLocation = left + (strWidth + 4) * progress;

                    RenderingUtil.rectangle(left, 0, healthLocation, 1.5, Colors.getColorOpacity(customColor.getRGB(), (int) (percentage * 255)));
                    RenderingUtil.rectangle(healthLocation, 0, left + difference, 1.5, Colors.getColor(160, 0, 0, (int) (percentage * 255)));


                    if (absorption > 0) {
                        double absorptionDifferent = difference * (absorption / (ent.getMaxHealth() + absorption));

                        RenderingUtil.rectangle(healthLocation, 0, absorptionDifferent + healthLocation, 1.5, Colors.getColorOpacity(0xFFFFAA00, (int) (percentage * 255)));
                    }

                    RenderingUtil.rectangle(left, 1, left + difference, 1.5, Colors.getColor(0, (int) (percentage * 110)));
                }

                boolean healthOption = selectHealth.equals("Always") || (isPriority && selectHealth.equals("Priority Only"));
                if (healthOption || hovered && selectHealth.equals("Hover")) {
                    String healthInfo = String.valueOf(MathUtils.roundToPlace(health / 2F, 1)).replaceFirst("\\.0", "") +
                            (ent.getAbsorptionAmount() > 0 ? " \2476" + String.valueOf((int) ent.getAbsorptionAmount() / 2F).replaceFirst("\\.0", "") : "");

                    float strWidth2 = font.getWidth(healthInfo);

                    double fullWidth = Math.max(strWidth2 + 2, 11);

                    RenderingUtil.rectangleBordered(strWidth / 2 + 3, -11, strWidth / 2 + 3 + fullWidth, 0, 0.5, backgroundColor, borderColor);
                    font.drawBorderedString(healthInfo, strWidth / 2 + 3.5 + fullWidth / 2 - strWidth2 / 2, -8.5, Colors.getColor(customColor.getRed(), customColor.getGreen(), customColor.getBlue(), (int) (255 * percentage)), Colors.getColor(customColor.getRed(), customColor.getGreen(), customColor.getBlue(), (int) (190 * percentage)));
                }

                boolean armor = selectArmor.equals("Always") || (isPriority && selectArmor.equals("Priority Only"));
                if (armor || hovered && selectArmor.equals("Hover") || isPriority) {
                    List<ItemStack> itemsToRender = new ArrayList<>();
                    for (int i = 0; i < 5; i++) {
                        ItemStack stack = ent.getEquipmentInSlot(i);
                        if (stack != null) {
                            itemsToRender.add(stack);
                        }
                    }
                    int x = -5 - (itemsToRender.size() * 5);
                    for (ItemStack stack : itemsToRender) {

                        boolean stackDamaged = stack.getItemDamage() > 0 && stack.getMaxDamage() - stack.getItemDamage() > 0;
                        int bruh = stackDamaged ? -27 : -24;

                        RenderingUtil.rectangle(x, bruh, x + 11, bruh + 11, backgroundColor);

                        RenderHelper.enableGUIStandardItemLighting();
                        GlStateManager.translate(x - 0.5, bruh, 0);
                        GlStateManager.scale(0.75, 0.75, 0.75);
                        mc.getRenderItem().renderItemIntoGUI(stack, 0, -1);
                        mc.getRenderItem().renderItemOverlays(mc.fontRendererObj, stack, 0, -1);
                        GlStateManager.scale(1 / 0.75, 1 / 0.75, 1 / 0.75);
                        GlStateManager.translate(-x + 0.5, -bruh, 0);
                        RenderHelper.disableStandardItemLighting();
                        x += 3;
                        int y = 21;

                        boolean showPitEnchants = HypixelUtil.isInGame("THE HYPIXEL PIT");
                        if (showPitEnchants && HypixelUtil.isItemMystic(stack)) {
                            List<String> enchants = HypixelUtil.getPitEnchants(stack);

                            List<String> render = new ArrayList<>();

                            int enchantOffsetY = 0;

                            for (String e : enchants) {
                                boolean strongEnchant = e.contains("Mind Assault") || e.contains("Retro") || e.contains("Stun") || e.contains("Funky") || e.contains("Protection III") ||
                                        e.contains("Wrath I") || e.contains("Duelist I") || e.contains("Bruiser") || e.contains("David") || e.contains("Somber") ||
                                        e.contains("Billionaire I") || e.contains("Hemorrhage") || e.contains("Mirror") || e.contains("Evil Within") ||
                                        e.contains("Venom") || e.contains("Gamble") || e.contains("Crush") || e.contains("Solitude") || e.contains("Peroxide") ||
                                        e.contains("Diamond Allergy") || e.contains("Hunt the Hunter") || e.contains("Regularity");

                                int level = 1;

                                if (e.length() > 1) {
                                    StringBuilder temp = new StringBuilder();
                                    for (String s : StringUtils.stripHypixelControlCodes(e)
                                            .replace("\247f\2477\2479", "")
                                            .replace("“", "")
                                            .replace("”", "")
                                            .replace("\"", "")
                                            .replace("(", "").split(" ")) {
                                        if (s.contains("RARE") || s.length() < 1) {
                                            continue;
                                        }

                                        if (!s.startsWith("II")) {
                                            temp.append(s.charAt(0));
                                        } else {
                                            level += s.equals("II") ? 1 : 2;
                                        }
                                    }
                                    if (!temp.toString().equals("")) {
                                        render.add((strongEnchant ? "\247c\247l" : "\247e\247l") + temp + getColor(level) + "\247l" + level);
                                    }
                                }
                            }

                            render.sort(Comparator.comparingInt(String::length));

                            for (String string : render) {
                                drawEnchantTag(string, x - 3, y + 7 + enchantOffsetY);
                                enchantOffsetY += 10;
                            }
                        }

                        if (stack.getItem() instanceof ItemSword) {
                            int sLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack);
                            int fLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack);
                            int kLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack);
                            if (sLevel > 0) {
                                drawEnchantTag("Sh" + getColor(sLevel) + sLevel, x, y);
                                y -= 9;
                            }
                            if (fLevel > 0) {
                                drawEnchantTag("Fir" + getColor(fLevel) + fLevel, x, y);
                                y -= 9;
                            }
                            if (kLevel > 0) {
                                drawEnchantTag("Kb" + getColor(kLevel) + kLevel, x, y);
                            }
                        } else if ((stack.getItem() instanceof ItemArmor)) {
                            int pLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
                            int tLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, stack);
                            int uLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
                            if (pLevel > 0) {
                                drawEnchantTag("P" + getColor(pLevel) + pLevel, x, y);
                                y -= 9;
                            }
                            if (tLevel > 0) {
                                drawEnchantTag("Th" + getColor(tLevel) + tLevel, x, y);
                                y -= 9;
                            }
                            if (uLevel > 0) {
                                drawEnchantTag("Unb" + getColor(uLevel) + uLevel, x, y);
                            }
                        } else if ((stack.getItem() instanceof ItemBow)) {
                            int powLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId,
                                    stack);
                            int punLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId,
                                    stack);
                            int fireLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack);
                            if (powLevel > 0) {
                                drawEnchantTag("Pow" + getColor(powLevel) + powLevel, x, y);
                                y -= 9;
                            }
                            if (punLevel > 0) {
                                drawEnchantTag("Pun" + getColor(punLevel) + punLevel, x, y);
                                y -= 9;
                            }
                            if (fireLevel > 0) {
                                drawEnchantTag("Fir" + getColor(fireLevel) + fireLevel, x, y);
                            }
                        } else if (stack.getRarity() == EnumRarity.EPIC) {
                            drawEnchantTag("\2476\247lGod", x, y);
                        }

                        int potionEffect = (int) Math.round(255.0D - (double) stack.getItemDamage() * 255.0D / (double) stack.getMaxDamage());
                        int var10 = 255 - potionEffect << 16 | potionEffect << 8;
                        Color potionColor = new Color(var10).brighter();

                        int x2 = (x * 2);
                        if (stackDamaged) {
                            String aa = "" + (stack.getMaxDamage() - stack.getItemDamage());
                            double width = mc.fontRendererObj.getStringWidth(aa);
                            GlStateManager.pushMatrix();
                            GlStateManager.disableDepth();
                            GL11.glScalef(0.5F, 0.5F, 0.5F);
                            mc.fontRendererObj.drawStringWithShadow(aa, x2 - width / 4D, -9 - y, potionColor.getRGB());
                            GlStateManager.enableDepth();
                            GlStateManager.popMatrix();
                        }
                        x += 10;
                    }
                }

                GlStateManager.popMatrix();


            }

            entityPositions.clear();
        }
    }

    private String getColor(int level) {
        switch (level) {
            case 1:
                return "\247f";
            case 2:
                return "\247a";
            case 3:
                return "\2473";
            case 4:
                return "\2474";
            case 5:
                return "\2476";
        }
        return "\247f";
    }

    private static void drawEnchantTag(String text, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        x = (int) (x * 1.75D);
        y -= 4;
        GL11.glScalef(0.57F, 0.57F, 0.57F);
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, x, -30 - y, Colors.getColor(255));
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void scale() {
        float scale = 1;
        scale *= ((mc.currentScreen == null) && (GameSettings.isKeyDown(mc.gameSettings.ofKeyBindZoom)) ? 1.5 : 1);
        GlStateManager.scale(scale, scale, scale);
    }

    private void updatePositions() {
        entityPositions.clear();
        float pTicks = mc.timer.renderPartialTicks;

        boolean ignorePit = HypixelUtil.isInGame("THE HYPIXEL PIT") && IGNORESPAWN.getValue();

        for (EntityPlayer ent : playerEntities) {
            if (ent != mc.thePlayer) {

                boolean isPriority = TargetESP.isPriority(ent) || FriendManager.isFriend(ent.getName());

                if (!isPriority) {
                    if (UUIDResolver.instance.isInvalidName(ent.getName())) {
                        isPriority = true;
                    }
                }

                if (priorityOnly.getValue() && !isPriority) {
                    continue;
                }

                if (ignorePit && !isPriority) {
                    double x = ent.posX;
                    double y = ent.posY;
                    double z = ent.posZ;
                    if (y > Client.instance.spawnY && x < 30 && x > -30 && z < 30 && z > -30) {
                        entityPositions.remove(ent);
                        continue;
                    }
                }

                if ((Boolean) settings.get(INVISIBLES).getValue() || !ent.isInvisible()) {
                    double x = ent.lastTickPosX + (ent.posX - ent.lastTickPosX) * pTicks - mc.getRenderManager().viewerPosX;
                    double y = ent.lastTickPosY + (ent.posY - ent.lastTickPosY) * pTicks - mc.getRenderManager().viewerPosY;
                    double z = ent.lastTickPosZ + (ent.posZ - ent.lastTickPosZ) * pTicks - mc.getRenderManager().viewerPosZ;
                    y += ent.height + 0.2D;

                    Bruh bruh;
                    if (entityPositions.containsKey(ent)) {
                        bruh = entityPositions.get(ent);
                    } else {
                        bruh = new Bruh(new double[4]);
                        entityPositions.put(ent, bruh);
                    }
                    RenderingUtil.worldToScreenOptimized(x, y, z, bruh);
                    if (bruh.array[0] == -1337 || (bruh.array[2] < 0.0D || bruh.array[2] > 1.0D)) {
                        entityPositions.remove(ent);
                    }
                }
            }
        }
    }

    public static class Bruh {

        public FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);
        public IntBuffer viewport = BufferUtils.createIntBuffer(16);
        public FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
        public FloatBuffer projection = BufferUtils.createFloatBuffer(16);

        // public boolean isImportant = false;

        public double[] array = new double[3];

        public Bruh(double[] array) {
            this.array[0] = array[0];
            this.array[1] = array[1];
            this.array[2] = array[2];
        }

    }

}
