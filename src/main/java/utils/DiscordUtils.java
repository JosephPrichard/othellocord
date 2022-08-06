package utils;

public class DiscordUtils
{
    public static long toLongId(String id) {
        String strippedId = id.replaceAll("[\\D]", "");
        return Long.parseLong(strippedId);
    }

    public static void main(String[] args) {
        System.out.println(Long.parseLong("168526026425499648"));
    }
}
