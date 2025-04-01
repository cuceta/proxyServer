public class Encryption {
    private static long xorShift(long r) {
        r ^= (r << 13);
        r ^= (r >>> 7);
        r ^= (r << 17);
        return r;
    }


    public static byte[] encryptDecrypt(byte[] data, long key) {
        byte[] result = new byte[data.length];
        long currentKey = key;
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte)(data[i] ^ (currentKey & 0xFF));
            currentKey = xorShift(currentKey);
        }
        return result;
    }
}
