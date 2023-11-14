/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

public class Discord
{
    public static long toLongId(String id) {
        // removes all non digit characters
        String strippedId = id.replaceAll("[\\D]", "");
        return Long.parseLong(strippedId);
    }

    public static void main(String[] args) {
        System.out.println(Long.parseLong("168526026425499648"));
    }
}
