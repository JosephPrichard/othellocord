/*
 * Copyright (c) Joseph Prichard 2023.
 */

package utils;

public class Array
{
    public static byte[][] deepCopyOf2DArray(byte[][] array) {
        var arrayCopy = new byte[array.length][];
        for (var i = 0; i < array.length; i++) {
            arrayCopy[i] = array[i].clone();
        }
        return arrayCopy;
    }

    public static byte[] copyOfArray(byte[] array) {
        var arrayCopy = new byte[array.length];
        System.arraycopy(array, 0, arrayCopy, 0, array.length);
        return arrayCopy;
    }
}
