package exhibition.util.security.hwid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import exhibition.Client;
import exhibition.util.security.DiscordUtil;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.util.Date;

import static com.sun.jna.platform.win32.Advapi32Util.*;
import static com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE;

public class HardwareIdentification implements Identifier {

    //OS
    public final OperatingSystemIdentifiers operatingSystemIdentifiers;

    //Processor
    public final String cpuName;

    // System
    public final SystemIdentifiers systemIdentifiers;

    // Baseboard
    public final BaseboardIdentifiers baseboardIdentifiers;

    // Firmware
    public final FirmwareIdentifiers firmwareIdentifiers;

    public HardwareIdentification(Object systemInfo) {

        OperatingSystem os = ((SystemInfo) systemInfo).getOperatingSystem();

        HardwareAbstractionLayer hardware = ((SystemInfo) systemInfo).getHardware();

        CentralProcessor centralProcessor = hardware.getProcessor();

        this.operatingSystemIdentifiers = new OperatingSystemIdentifiers(os);

        this.cpuName = trim(centralProcessor.getName());

        GlobalMemory memory = hardware.getMemory();
        ComputerSystem computerSystem = hardware.getComputerSystem();

        int totalRam = (int) Math.round(memory.getTotal() / Math.pow(1024, 3));

        this.systemIdentifiers = new SystemIdentifiers(computerSystem, totalRam);

        Baseboard baseboard = computerSystem.getBaseboard();

        this.baseboardIdentifiers = new BaseboardIdentifiers(baseboard);

        this.firmwareIdentifiers = new FirmwareIdentifiers(computerSystem.getFirmware());
    }

    public String getIdentifiersAsJson() {
        Gson gson = new GsonBuilder().create();

        JsonObject jsonObject = new JsonObject();

        if (Client.isDiscordReady) {
            JsonObject discordObject = new JsonObject();

            discordObject.addProperty("username", DiscordUtil.getDiscordUsername(discordObject).toString());
            discordObject.addProperty("id", DiscordUtil.getDiscordID(discordObject).toString());

            jsonObject.add("Discord", discordObject);
        }

        JsonObject osObject = new JsonObject();
        osObject.addProperty("name", operatingSystemIdentifiers.getFamily() + " " + operatingSystemIdentifiers.getVersion());
        osObject.addProperty("build", operatingSystemIdentifiers.getBuild());

        try {
            if (operatingSystemIdentifiers.getFamily().equals("Windows")) {
                String keyPath = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";
                if (registryKeyExists(HKEY_LOCAL_MACHINE, keyPath) && registryValueExists(HKEY_LOCAL_MACHINE, keyPath, "InstallDate")) {
                    long installDate = registryGetIntValue(HKEY_LOCAL_MACHINE, keyPath, "InstallDate") * 1000L;
                    osObject.addProperty("date", new Date(installDate).toString());
                }
            }
        } catch (Exception e) {
            //e.printStackTrace();
        }

        jsonObject.add("OS", osObject);

        JsonObject cpuObject = new JsonObject();
        cpuObject.addProperty("name", cpuName);

        jsonObject.add("CPU", cpuObject);

        JsonObject baseboardObject = new JsonObject();
        baseboardObject.addProperty("manufacturer", baseboardIdentifiers.getManufacturer());
        baseboardObject.addProperty("model", baseboardIdentifiers.getModel());
        baseboardObject.addProperty("serial", baseboardIdentifiers.getSerial());

        jsonObject.add("Baseboard", baseboardObject);

        JsonObject systemObject = new JsonObject();
        systemObject.addProperty("manufacturer", systemIdentifiers.getManufacturer());
        systemObject.addProperty("model", systemIdentifiers.getModel());
        systemObject.addProperty("serial", systemIdentifiers.getSerial());
        systemObject.addProperty("totalram", systemIdentifiers.getTotalRam());

        jsonObject.add("System", systemObject);

        JsonObject firmwareObject = new JsonObject();
        firmwareObject.addProperty("manufacturer", firmwareIdentifiers.getManufacturer());
        firmwareObject.addProperty("date", firmwareIdentifiers.getDate());
        firmwareObject.addProperty("version", firmwareIdentifiers.getVersion());

        jsonObject.add("Firmware", firmwareObject);

        JsonArray displayArray = new JsonArray();

        jsonObject.add("Displays", displayArray);

        JsonArray graphicsArray = new JsonArray();

        jsonObject.add("Graphics", graphicsArray);

        JsonArray diskArray = new JsonArray();

        jsonObject.add("Disks", diskArray);

        JsonArray vadaptersArray = new JsonArray();
        JsonArray nadaptersArray = new JsonArray();

        if (nadaptersArray.size() > 0) {
            jsonObject.add("Network_Adapters", nadaptersArray);
        }

        if (vadaptersArray.size() > 0) {
            jsonObject.add("Virtual_Adapters", vadaptersArray);
        }

        return gson.toJson(jsonObject);
    }

    //     $hwidStr = $jsonObj['CPU']['name'] . $jsonObj['Baseboard']['model'] . $jsonObj['Baseboard']['serial'] . $jsonObj['System']['model'] . $jsonObj['System']['serial']
    public String getHashedHardware() {
        String str = cpuName + baseboardIdentifiers.getModel() + baseboardIdentifiers.getSerial() + systemIdentifiers.getModel() + systemIdentifiers.getSerial();

        return str.trim();
    }

}
