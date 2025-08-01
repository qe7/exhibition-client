package exhibition.gui.altmanager;

import org.apache.logging.log4j.LogManager;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public class Alts extends FileManager.CustomFile {
    public Alts(final String name, final boolean Module, final boolean loadOnStart) {
        super(name, Module, loadOnStart);
    }

    @Override
    public void loadFile() throws IOException {
        Scanner scanner = new Scanner(this.getFile());
        String altName = null;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.equals("") || line.equals("\n") || !line.contains(":"))
                continue;

            if (!line.startsWith("lastalt:")) {
                final String[] arguments = line.split(" ")[0].split(" \\(")[0].replace(" - SFA", "").replace(" | SFA", "").split(":");
                if (arguments.length >= 5) {
                    long time = (Long.parseLong(arguments[4]) > System.currentTimeMillis() ? Long.parseLong(arguments[4]) : -1);
                    Alt alt = new Alt(arguments[0], arguments[1], arguments[2], Alt.Status.valueOf(arguments[3]), time);

                    if (arguments.length >= 6) {
                        alt.setFavorite(Boolean.parseBoolean(arguments[5]));
                    }

                    AltManager.registry.add(alt);
                } else {
                    AltManager.registry.add(new Alt(arguments[0], arguments[1]));
                }
            } else if (line.startsWith("lastalt:")) {
                altName = line.substring(8);
            }
        }
        if (altName != null) {
            for (Alt alt : AltManager.registry) {
                if (alt.getMask().equals(altName)) {
                    AltManager.lastAlt = alt;
                }
            }
        }
        scanner.close();
        LogManager.getLogger().info("Loaded " + this.getName() + " File!");
    }

    @Override
    public void saveFile() throws IOException {
        final PrintWriter alts = new PrintWriter(new FileWriter(this.getFile()));
        for (final Alt alt : AltManager.registry) {
            if (alt.getMask().equals("")) {
                alts.println(alt.getUsername() + ":" + alt.getPassword() + ":" + alt.getUsername() + ":" + alt.getStatus() + ":" + alt.getUnbanDate() + ":" + alt.isFavorite());
            } else {
                alts.println(alt.getUsername() + ":" + alt.getPassword() + ":" + alt.getMask() + ":" + alt.getStatus() + ":" + alt.getUnbanDate() + ":" + alt.isFavorite());
            }
        }
        if (AltManager.lastAlt != null) {
            alts.println("lastalt:" + AltManager.lastAlt.getMask());
        }
        alts.close();
    }
}
