/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

public class Number
{
    public static Integer parseIntOrNull(String str) {
        try {
            return Integer.parseInt(str);
        } catch(NumberFormatException e){
            return null;
        }
    }
}
