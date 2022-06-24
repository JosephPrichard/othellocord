package bot.utils;

public class StringUtils
{
    public static String column(String str, int size, String tail) {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        // output chars until amount
        for (; i < str.length() && i < size; i++) {
            builder.append(str.charAt(i));
        }
        // output spaces to make padding until remaining amount
        for (; i < size; i++) {
            builder.append(' ');
        }
        builder.append(tail);
        return builder.toString();
    }

    public static String rightPad(String str, int size) {
        return column(str, size, "");
    }

    public static String leftPad(String str, int size) {
        StringBuilder builder = new StringBuilder();
        // output spaces first
        builder.append(" ".repeat(Math.max(0, size - str.length())));
        // output text until remaining amount
        for (int i = 0; i < str.length(); i++) {
            builder.append(str.charAt(i));
        }
        return builder.toString();
    }
}
