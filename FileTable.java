import java.util.Vector;

public class FileTable {

    private Vector table = new Vector<FileTableEntry>();         // the actual entity of this file table
    private Directory dir;        // the root directory

    public FileTable(Directory directory) { // constructor
        dir = directory;           // receive a reference to the Director
    }                             // from the file system


    public synchronized FileTableEntry falloc(String filename, String mode) {
        // Check for incorrect filename input
//        if (filename == null || filename.isEmpty()) {
//            return null;
//        }
        // Check for incorrect mode input

        short iNumber = -1;
        Inode node = null;
        while (true) {
            // Set iNumber
            if (filename.equals("/")) {
                iNumber = 0;
            } else {
                iNumber = dir.namei(filename);
            }

            if (iNumber >= 0) {
                node = new Inode(iNumber);
                if (mode.equals("r")) {
                    if (node.flag != 0 && node.flag != 1) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                        }
                        continue;
                    }
                    node.flag = 1;
                    break;
                }

                if (node.flag != 0 && node.flag != 3) {
                    if (node.flag == 1 || node.flag == 2) {
                        node.flag = (short)(node.flag + 3);
                        node.toDisk(iNumber);
                    }

                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                    }
                    continue;
                }

                node.flag = 2;
                break;
            }
            if (mode.equals("r")) {
                return null;
            }

            iNumber = this.dir.ialloc(filename);
            node = new Inode();
            node.flag = 2;
            break;
        }
        ++node.count;
        node.toDisk(iNumber);
        FileTableEntry tblEntry = new FileTableEntry(node, iNumber, mode);
        table.addElement(tblEntry);
        return tblEntry;
    }

    public synchronized boolean ffree(FileTableEntry entry) {
        // Check for null entry being given
        if (entry == null) {
            return true;
        }

        // Attempt to remove, return false if entry not found
        if (!table.removeElement(entry)) {
            return false;
        }
        entry.inode.count--;

        if (entry.inode.flag == 1 || entry.inode.flag == 2) {
            entry.inode.flag = 0;
        }
        if (entry.inode.flag == 4 || entry.inode.flag == 5) {
            entry.inode.flag = 3;
        }

        // Write inode to disk
        entry.inode.toDisk(entry.iNumber);

        // Set entry to null
        entry = null;
        notify();
        return true;
}

    public synchronized boolean fempty() {
        return table.isEmpty();  // return if table is empty
    }                            // should be called before starting a format
}
