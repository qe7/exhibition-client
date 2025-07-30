package exhibition.management;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import exhibition.Client;
import exhibition.management.notifications.usernotification.Notifications;
import exhibition.module.impl.other.NickDetector;
import exhibition.util.HypixelUtil;
import exhibition.util.Timer;
import exhibition.util.security.Connection;
import exhibition.util.security.Connector;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.Serializable;
import java.util.*;

public class UUIDResolver {

    public static UUIDResolver instance = new UUIDResolver();

    public boolean isChecking;

    public Gson gson = new Gson();

    public HashMap<String, Long> validMap = new HashMap<>();
    public HashMap<String, String> resolvedMap = new HashMap<>();
    public HashMap<String, UUID> checkedUsernames = new HashMap<>();

    private final HashMap<Integer, Long> responseMap = new HashMap<>();
    private final HashMap<Integer, Long> hypixelResponseMap = new HashMap<>();
    private boolean isRateLimited;

    public boolean isInvalidName(String username) {
        if (!checkedUsernames.containsKey(username)) {
            return false;
        }

        if (!validMap.containsKey(username)) {
            return true;
        }

        return false;
    }

    public boolean isInvalidUUID(UUID uuid) {
        String username = null;

        Iterator<Map.Entry<String, UUID>> usernames = new HashMap<>(checkedUsernames).entrySet().iterator();
        while (usernames.hasNext()) {
            Map.Entry<String, UUID> entry = usernames.next();
            if (entry.getValue().equals(uuid)) {
                username = entry.getKey();
            }
        }

        if (username == null)
            return false;

        if (!validMap.containsKey(username)) {
            return true;
        }

        return false;
    }

    public boolean isSkinValid(GameProfile profile) {
        PropertyMap props = profile.getProperties();
        if (props.containsKey("textures")) {
            Collection<Property> proplist = props.get("textures");
            for (Property prop : proplist) {
                String textureJson = new String(Base64.getDecoder().decode(prop.getValue()));
                JsonObject jsonObject = new JsonParser().parse(textureJson).getAsJsonObject();
                //SkinTextureData skindata = gson.fromJson(textureJson, SkinTextureData.class);

                long timestamp = jsonObject.get("timestamp").getAsLong();

                String profileName = jsonObject.get("profileName").getAsString();

                if (!prop.hasSignature() || (timestamp < System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000)) {
                    return false;
                } else if (!profileName.equalsIgnoreCase(profile.getName())) {
                    resolvedMap.put(profile.getName(), profileName);
                    return false;
                }
            }
        }
        return true;
    }

    public class SkinTextureData implements Serializable {
        public long timestamp;
        public String profileName;
    }

    public void checkNames(HashMap<String, UUID> usernamesToCheck) {

        try {
            Iterator<Map.Entry<String, Long>> validMapIter = validMap.entrySet().iterator();
            while (validMapIter.hasNext()) {
                Map.Entry<String, Long> entry = validMapIter.next();
                if (entry.getValue() + 1_800_000 < System.currentTimeMillis()) { // 30 Minutes
                    checkedUsernames.remove(entry.getKey());
                    validMapIter.remove();
                }
            }
            Iterator<Map.Entry<Integer, Long>> responses = responseMap.entrySet().iterator();
            while (responses.hasNext()) {
                Map.Entry<Integer, Long> map = responses.next();
                if (map.getValue() + 600_000 < System.currentTimeMillis()) {
                    responses.remove();
                }
            }
            Iterator<Map.Entry<Integer, Long>> hypixelRespones = hypixelResponseMap.entrySet().iterator();
            while (hypixelRespones.hasNext()) {
                Map.Entry<Integer, Long> map = hypixelRespones.next();
                if (map.getValue() + 60_000 < System.currentTimeMillis()) {
                    hypixelRespones.remove();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (responseMap.size() < 600) {
            if (isRateLimited && responseMap.size() < 550) {
                isRateLimited = false;
                Notifications.getManager().post("Nick Detector", "No longer rate limited. Checking again.", 1500, Notifications.Type.OKAY);
            }
            if (!isRateLimited) {
                CheckThread checkThread = new CheckThread(usernamesToCheck);
                checkThread.start();
            }
        } else {
            if (!isRateLimited) {
                isRateLimited = true;
                Notifications.getManager().post("Nick Detector", "Rate limit reached. Waiting for requests to expire.", 2500, Notifications.Type.NOTIFY);
            }
        }
    }

    private void resolveNames(HashMap<String, UUID> names) {

        try {
            Connection testConnection = new Connection("https://api.mojang.com/profiles/minecraft");
            testConnection.setContentType("application/json");

            JsonArray jsonArray = new JsonArray();
            for (Map.Entry<String, UUID> entry : names.entrySet()) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("name", entry.getKey());
                jsonObject.addProperty("id", entry.getValue().toString().replace("-", ""));
                jsonArray.add(jsonObject);
            }
            testConnection.setJson(gson.toJson(jsonArray));
            Connector.post(testConnection);

            JsonArray resultArray = new JsonParser()
                    .parse(testConnection.getResponse())
                    .getAsJsonArray();

            long current = System.currentTimeMillis();
            for (JsonElement jsonElement : resultArray) {
                JsonObject jsonObject = (JsonObject) jsonElement;
                String name = jsonObject.get("name").getAsString();
                validMap.put(name, current);
            }

            responseMap.put(names.toString().hashCode(), current);

            for (Map.Entry<String, UUID> entry : names.entrySet()) {
                if (!validMap.containsKey(entry.getKey()) && !checkedUsernames.containsKey(entry.getKey())) {
                    Notifications.getManager().post("Nick Detector", entry.getKey() + " is not a real player name!", 2500, Notifications.Type.NOTIFY);
                }
                checkedUsernames.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception ignored) {
        }
    }

    public class CheckThread extends Thread {

        private final HashMap<String, UUID> usernameList;

        public CheckThread(HashMap<String, UUID> usernames) {
            this.usernameList = usernames;
        }

        @Override
        public void run() {
            e();
        }

        public void e() {
            isChecking = true;
            try {
                // Resolve items first
                NickDetector nickDetector = Client.getModuleManager().get(NickDetector.class);
                try {
                    for (Iterator<NickDetector.ResolvePair> it = nickDetector.resolvePairList.iterator(); it.hasNext(); ) {
                        NickDetector.ResolvePair pair = it.next();

                        if(resolvedMap.containsKey(pair.getUsername()))
                            continue;

                        ItemStack stack = pair.getStack();
                        if (stack != null && stack.hasTagCompound()) {
                            if (stack.getTagCompound().hasKey("ExtraAttributes", 10)) {
                                NBTTagCompound nbttagcompound = stack.getTagCompound().getCompoundTag("ExtraAttributes");
                                if (nbttagcompound.hasKey("Nonce", 3)) {
                                    try {
                                        long nonceLong = nbttagcompound.getLong("Nonce");

                                        if (nonceLong <= 100)
                                            continue;

                                        String nonce = String.valueOf(nonceLong);

                                        Connection pitPandaSearch = new Connection("https://pitpanda.rocks/api/itemsearch/nonce" + nonce);

                                        Connector.get(pitPandaSearch);
                                        JsonObject jsonObject = new JsonParser()
                                                .parse(pitPandaSearch.getResponse())
                                                .getAsJsonObject();
                                        boolean success = jsonObject.get("success").getAsBoolean();
                                        if (success) {
                                            boolean itemsNull = jsonObject.get("items").isJsonNull();
                                            if (!itemsNull) {
                                                JsonArray test = jsonObject.get("items").getAsJsonArray();
                                                if (test.size() > 0) {
                                                    JsonObject element = test.get(0).getAsJsonObject();
                                                    if (element.has("owner")) {
                                                        String uuid = element.get("owner").getAsString();
                                                        Connection profileConnection = new Connection("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                                                        Connector.get(profileConnection);
                                                        JsonObject profileJsonObject = new JsonParser()
                                                                .parse(profileConnection.getResponse())
                                                                .getAsJsonObject();
                                                        responseMap.put(profileConnection.getResponse().hashCode(), System.currentTimeMillis());

                                                        if (profileJsonObject.has("name")) {
                                                            String resolvedName = profileJsonObject.get("name").getAsString();
                                                            if (resolvedName != null) {
                                                                resolvedMap.put(pair.getUsername(), resolvedName);
                                                                Notifications.getManager().post("Nick Detector", pair.getUsername() + " may be " + resolvedName + "! (N1)", 2500, Notifications.Type.NOTIFY);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {

                                    }
                                    it.remove();
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {

                }

                List<String> recentlyResolved = new ArrayList<>();

                HashMap<String, UUID> tempList = new HashMap<>();

                for (Map.Entry<String, UUID> entry : usernameList.entrySet()) {
                    if (!isChecking || Minecraft.getMinecraft().thePlayer == null)
                        return;
                    if (checkedUsernames.containsKey(entry.getKey()))
                        continue;
                    tempList.put(entry.getKey(), entry.getValue());
                    recentlyResolved.add(entry.getKey());
                    if (tempList.size() == 10) {
                        resolveNames(tempList);
                        tempList.clear();
                    }
                }

                if (!tempList.isEmpty() && tempList.size() <= 10) {
                    resolveNames(tempList);
                }

                try {
                    if (isChecking && HypixelUtil.isInGame("PIT") && nickDetector.denick.getValue()) {
                        for (String username : usernameList.keySet()) {
                            if (!validMap.containsKey(username) && checkedUsernames.containsKey(username) && !resolvedMap.containsKey(username)) {
                                if (!isChecking || !nickDetector.denick.getValue() || Minecraft.getMinecraft().thePlayer == null || Minecraft.getMinecraft().theWorld == null)
                                    continue;
                                Minecraft mc = Minecraft.getMinecraft();

                                EntityPlayer player = mc.theWorld.getPlayerEntityByName(username);
                                if (player == null)
                                    continue;

                                for (int i = 0; i < 45; i++) {
                                    ItemStack stack = player.inventoryContainer.getSlot(i).getStack();
                                    if (stack != null && stack.hasTagCompound()) {
                                        if (stack.getTagCompound().hasKey("ExtraAttributes", 10)) {
                                            NBTTagCompound nbttagcompound = stack.getTagCompound().getCompoundTag("ExtraAttributes");

                                            if (nbttagcompound.hasKey("Nonce", 3)) {
                                                try {

                                                    long nonceLong = nbttagcompound.getLong("Nonce");

                                                    if (nonceLong <= 100)
                                                        continue;

                                                    String nonce = String.valueOf(nonceLong);

                                                    Connection pitPandaSearch = new Connection("https://pitpanda.rocks/api/itemsearch/nonce" + nonce);

                                                    Connector.get(pitPandaSearch);
                                                    JsonObject jsonObject = new JsonParser()
                                                            .parse(pitPandaSearch.getResponse())
                                                            .getAsJsonObject();
                                                    boolean success = jsonObject.get("success").getAsBoolean();
                                                    if (success) {
                                                        boolean itemsNull = jsonObject.get("items").isJsonNull();
                                                        if (!itemsNull) {
                                                            JsonArray test = jsonObject.get("items").getAsJsonArray();
                                                            if (test.size() > 0) {
                                                                JsonObject element = test.get(0).getAsJsonObject();
                                                                if (element.has("owner")) {
                                                                    String uuid = element.get("owner").getAsString();
                                                                    Connection profileConnection = new Connection("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                                                                    Connector.get(profileConnection);
                                                                    JsonObject profileJsonObject = new JsonParser()
                                                                            .parse(profileConnection.getResponse())
                                                                            .getAsJsonObject();
                                                                    responseMap.put(profileConnection.getResponse().hashCode(), System.currentTimeMillis());

                                                                    if (profileJsonObject.has("name")) {
                                                                        String resolvedName = profileJsonObject.get("name").getAsString();
                                                                        if (resolvedName != null) {
                                                                            if (!username.equals(resolvedName)) {
                                                                                resolvedMap.put(username, resolvedName);
                                                                                Notifications.getManager().post("Nick Detector", username + " may be " + resolvedName + "! (N2)", 2500, Notifications.Type.NOTIFY);
                                                                            }
                                                                            break;
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    e.printStackTrace();

                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                List<String> nickedUsers = new ArrayList<>();

                Timer timer = new Timer();

                try {
                    if (isChecking && Client.instance.hypixelApiKey != null && !Client.instance.hypixelApiKey.equals("") && hypixelResponseMap.size() <= 120) {
                        for (String username : usernameList.keySet()) {
                            String response = "";
                            try {
                                if (!isChecking || Minecraft.getMinecraft().thePlayer == null)
                                    return;
                                if (validMap.containsKey(username) && checkedUsernames.containsKey(username)) {
                                    Connection hypixelApiConnection = new Connection("https://api.hypixel.net/player");

                                    hypixelApiConnection.setParameters("key", Client.instance.hypixelApiKey);
                                    hypixelApiConnection.setParameters("uuid", usernameList.get(username).toString());

                                    Connector.get(hypixelApiConnection);

                                    JsonObject jsonObject = new JsonParser()
                                            .parse(hypixelApiConnection.getResponse())
                                            .getAsJsonObject();

                                    boolean success = jsonObject.get("success").getAsBoolean();

                                    if (success) {
                                        boolean playerNull = jsonObject.get("player").isJsonNull();
                                        if (playerNull) {
                                            Notifications.getManager().post("Nick Detector", username + " is in /nick! (Valid Name)", 2500, Notifications.Type.NOTIFY);
                                            validMap.remove(username);
                                            nickedUsers.add(username);
                                        }
                                    }

                                    hypixelResponseMap.put(username.hashCode(), System.currentTimeMillis());
                                }
                            } catch (Exception e) {
                                System.out.println(response);
                                e.printStackTrace();
                            }
                            Thread.sleep(Math.min(Math.max(1000 - timer.getDifference(), 0), 1000) + 100);
                            timer.reset();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    if (isChecking && HypixelUtil.isInGame("PIT") && nickDetector.denick.getValue()) {
                        for (String username : nickedUsers) {
                            if (!validMap.containsKey(username) && !resolvedMap.containsKey(username)) {
                                if (!isChecking || !nickDetector.denick.getValue() || Minecraft.getMinecraft().thePlayer == null || Minecraft.getMinecraft().theWorld == null)
                                    continue;
                                Minecraft mc = Minecraft.getMinecraft();

                                EntityPlayer player = mc.theWorld.getPlayerEntityByName(username);
                                if (player == null)
                                    continue;

                                for (int i = 0; i < 45; i++) {
                                    ItemStack stack = player.inventoryContainer.getSlot(i).getStack();
                                    if (stack != null && stack.hasTagCompound()) {
                                        if (stack.getTagCompound().hasKey("ExtraAttributes", 10)) {
                                            NBTTagCompound nbttagcompound = stack.getTagCompound().getCompoundTag("ExtraAttributes");

                                            if (nbttagcompound.hasKey("Nonce", 3)) {
                                                try {

                                                    long nonceLong = nbttagcompound.getLong("Nonce");

                                                    if (nonceLong <= 100)
                                                        continue;

                                                    String nonce = String.valueOf(nonceLong);

                                                    Connection pitPandaSearch = new Connection("https://pitpanda.rocks/api/itemsearch/nonce" + nonce);

                                                    Connector.get(pitPandaSearch);
                                                    JsonObject jsonObject = new JsonParser()
                                                            .parse(pitPandaSearch.getResponse())
                                                            .getAsJsonObject();
                                                    boolean success = jsonObject.get("success").getAsBoolean();
                                                    if (success) {
                                                        boolean itemsNull = jsonObject.get("items").isJsonNull();
                                                        if (!itemsNull) {
                                                            JsonArray test = jsonObject.get("items").getAsJsonArray();
                                                            if (test.size() > 0) {
                                                                JsonObject element = test.get(0).getAsJsonObject();
                                                                if (element.has("owner")) {
                                                                    String uuid = element.get("owner").getAsString();
                                                                    Connection profileConnection = new Connection("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                                                                    Connector.get(profileConnection);
                                                                    JsonObject profileJsonObject = new JsonParser()
                                                                            .parse(profileConnection.getResponse())
                                                                            .getAsJsonObject();
                                                                    responseMap.put(username.hashCode(), System.currentTimeMillis());

                                                                    if (profileJsonObject.has("name")) {
                                                                        String resolvedName = profileJsonObject.get("name").getAsString();
                                                                        if (resolvedName != null) {
                                                                            resolvedMap.put(username, resolvedName);
                                                                            Notifications.getManager().post("Nick Detector", username + " may be " + resolvedName + "! (N3)", 2500, Notifications.Type.NOTIFY);
                                                                            break;
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } catch (Exception e) {
                e.printStackTrace();
                isChecking = false;
            }
            isChecking = false;
        }

    }

}
