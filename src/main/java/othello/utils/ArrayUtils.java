package othello.utils;

public class ArrayUtils
{
    public static byte[][] deepCopyOf2DArray(byte[][] array) {
        byte[][] arrayCopy = new byte[array.length][];
        for (int i = 0; i < array.length; i++) {
            arrayCopy[i] = array[i].clone();
        }
        return arrayCopy;
    }

    public static byte[] copyOfArray(byte[] array) {
        byte[] arrayCopy = new byte[array.length];
        System.arraycopy(array, 0, arrayCopy, 0, array.length);
        return arrayCopy;
    }
}
