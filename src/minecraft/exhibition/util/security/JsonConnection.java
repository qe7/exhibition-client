package exhibition.util.security;

import com.google.gson.JsonParser;

public class JsonConnection {

    public static Object toJsonObject(Connection connection) {
        return new JsonParser()
                .parse(connection.getResponse())
                .getAsJsonObject();
    }

}
