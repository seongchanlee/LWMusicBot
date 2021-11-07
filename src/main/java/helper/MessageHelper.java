package helper;

import model.Commands;

public class MessageHelper {
    private MessageHelper() {
        // private constructor to avoid object instantiation
    }

    public static boolean isBotMessage(String msg) {
        return msg.split(Commands.BOT_SEPARATOR)[0].equals(Commands.BOT_PREFIX);
    }
}
