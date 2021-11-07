import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

import javax.security.auth.login.LoginException;

public class Main {
    public static void main(String[] args) throws LoginException {
        if (args.length < 1) {
            System.out.println("Usage: java -jar yourjarfile.jar [token]");
            return;
        }

        String credential = args[0];

        JDABuilder builder = JDABuilder.createDefault(credential);
        builder.setActivity(Activity.listening("top of the morning"));
        builder.addEventListeners(new MessageHandler());
        builder.build();
    }
}
