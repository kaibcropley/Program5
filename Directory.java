public class Directory {
    private static int maxChars = 30; // max characters of each file name
    private static int intSize = 4;

    // Directory entries
    private int fsize[];        // each element stores a different file size.
    private char fnames[][];    // each element stores a different file name.

    // Directory constructor
    // Sets all file sizes to 0
    public Directory(int maxInumber) { // directory constructor
        fsize = new int[maxInumber];     // maxInumber = max files
        for (int i = 0; i < maxInumber; i++)
            fsize[i] = 0;                 // all file size initialized to 0
        fnames = new char[maxInumber][maxChars];
        String root = "/";                // entry(inode) 0 is "/"
        fsize[0] = root.length();        // fsize[0] is the size of "/".
        root.getChars(0, fsize[0], fnames[0], 0); // fnames[0] includes "/"
    }

    // Receives data[] with directory information from disk and initializes
    // the directory with this instance of data[]
    // Returns false if data is incorrect, true if directory is initialized
    ////////////// ^
    public void bytes2directory(byte[] data) {
        // Check for incorrect input
//        if (data == null || data.length == 0) {
//            return false;
//        }

        int offset = 0;
        // Fill size array
        for (int i = 0; i < fsize.length; i++) {
            fsize[i] = SysLib.bytes2int(data, offset);
            offset += intSize;
        }
        // Fill name array
        for (int i = 0; i < fsize.length; i++) {
            String name = new String(data, offset, maxChars * 2);
            name.getChars(0, fsize[i], fnames[i], 0);
            offset += maxChars * 2;
        }
    }

    // Converts directory to a byte array and returns it
    // Byte array will be written back to disk
    public byte[] directory2bytes() {
        ///////////Check array size///////////////
        byte[] dirArr =
                new byte[fsize.length * intSize + fnames.length * maxChars * 2];
        int offset = 0;

        // Add all sizes
        for (int i : fsize) {
            SysLib.int2bytes(i, dirArr, offset);
            offset += intSize;
        }
        // Add all names
        for (int i = 0; i < fsize.length; i++) {
            byte[] bytes =
                    (new String(fnames[i], 0, fsize[i])).getBytes();
            System.arraycopy(bytes, 0, dirArr, offset, bytes.length);
            offset += maxChars * 2;
        }
        return dirArr;
    }

    // Receives a file name and attempts to add it to the directory
    // Returns index of filename as a short
    // Will return -1 if no open spot is found within directory
    public short ialloc(String filename) {
        // Search for open section
        for (int i = 0; i < fsize.length; i++) {
            if (fsize[i] == 0) {
                // Add filename to open section
                fsize[i] = Math.min(filename.length(), maxChars);
                filename.getChars(0, fsize[i], fnames[i], 0);
                return (short) i;
            }
        }
        return (short) -1;
    }

    // Deallocate the given inode number
    // The corresponding file will be deleted.
    public boolean ifree(short iNumber) {
       if (fsize[iNumber] > 0) {
           fsize[iNumber] = 0;
           return true;
       }
       return false;
    }

    // Takes a filename to check if the file is in the directory
    // Returns index of filename if found
    // Returns -1 in all other cases
    public short namei(String filename) {
        // Check for incorrect input
        // Search through names
        for (int i = 0; i < fnames.length; i++) {
            if (filename.length() == fsize[i]) {
                // Check if names are equal
                String fName = new String(fnames[i], 0, fsize[i]);
                if (filename.equals(fName)) {
                    return (short) i;
                }
            }
        }
        // No file with the same name was found
        return (short) -1;
    }
}
