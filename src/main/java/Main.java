import bot.JDASingleton;

import javax.security.auth.login.LoginException;

public class Main
{
    public static void main(String[] args) throws LoginException {
        if (args.length < 1) {
            System.out.println("You have to provide a Discord token as first argument!");
            System.exit(1);
        }

        System.out.println("Token: " + args[0]);

        JDASingleton.initJDASingleton(args[0]);
    }
}
