package othello.utils;

public class NumberUtils
{
    public static Integer parseIntOrNull(String str) {
        try {
            return Integer.parseInt(str);
        } catch(NumberFormatException e){
            return null;
        }
    }
}
