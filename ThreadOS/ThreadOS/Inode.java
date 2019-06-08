/**
 * Created by Phuc (Billy) Huynh on 5/31/2019
 */

public class Inode {

    // Initializing variables
    private final static int iNodeSize = 32;       // fix to 32 bytes
    private final static int directSize = 11;      // # direct pointers

    public int length;                             // file size in bytes
    public short count;                            // # file-table entries pointing to this
    public short flag;                             // 0 = unused, 1 = used, ...
    public short direct[] = new short[directSize]; // direct pointers
    public short indirect;                         // a indirect pointer, block number of another block

    /**
     * Default constructor:
     */
    public Inode() {
        length = 0;
        count = 0;
        flag = 1;
        for ( int i = 0; i < directSize; i++ )
            direct[i] = -1;						// indicates invalid block numbers
        indirect = -1;							// means no data blocks yet
    }

    /**
     * For existing file that already has an inode in the disk
     * Retrieves an existing inode from disk into the memory
     * Create an in memory representation (inode) of file on disk
     * Given an inode number, this constructor:
     * 		1. reads the corresponding disk block
     * 		2. locates the corresponding inode info in THAT block
     * 		3. intializes a NEW inode w/ this info
     */
    public Inode( short iNumber ) {
        // Find the corresponding inode from the disk by calculating disk block
        int blockNumber = 1 + (iNumber / 16);
        byte data[] = new byte[Disk.blockSize];  // setting the buffer size of a block 512 bytes
        // read from this blockNumber, the inode info, into this data buffer
        SysLib.rawread(blockNumber, data);
        // find where we are in the blockNumber of 512 bytes
        int offset = (iNumber % 16) * 32;
        this.length = SysLib.bytes2int(data, offset);
        offset += 4;
        this.count = SysLib.bytes2short(data, offset);
        offset += 2;
        this.flag = SysLib.bytes2short(data, offset);
        offset += 2;
        for(int i =0; i < directSize; i++) {
            this.direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        this.indirect = SysLib.bytes2short(data, offset);
    }

    /**
     * A write-back operation that saves this inode information
     * to the iNumber-th (given as argument) inode in the disk.
     */
    public void toDisk( short iNumber ) {
        // find block number
        byte data[] = new byte[Disk.blockSize];
        int blockNumber = 1 + (iNumber / 16);
        SysLib.rawread(blockNumber, data);
        // find offset in the block
        int offset = (iNumber % 16) * 32;
        SysLib.int2bytes(this.length, data, offset);
        offset += 4;
        SysLib.short2bytes(this.count, data, offset);
        offset += 2;
        SysLib.short2bytes(this.flag, data, offset);
        offset += 2;
        for(int i = 0; i < directSize; i++) {
            SysLib.short2bytes(this.direct[i], data, offset);
            offset += 2;
        }
        SysLib.short2bytes(this.indirect, data, offset);
        SysLib.rawwrite(blockNumber, data);
    }

    /**
     *  return indirect pointer
     */
    public short getIndexBlockNumber() {
        return this.indirect;
    }

    /**
     * Formats the indirect block
     * Return false if not all direct poitners are used or
     * if indirect pointer is used else, true
     */
    public boolean setIndexBlock(short indexBlockNumber) {
        // check if all direct pointers are used
        for(int i = 0; i < directSize; i++) {
            if(this.direct[i] == -1) {
                return false;
            }
        }
        // check if indirect pointer is UNUSED
        if(this.indirect != -1) {
            return true;
        }
        // set indirect pointer to indexBlock number
        this.indirect = indexBlockNumber;
        byte block[] = new byte[Disk.blockSize];
        // format the block to 512/2 = 256 pointers, set it to short -1 (2 bytes each)
        int offset = 0;
        short indexPtr = -1;
        for(int i = 0; i < 256; i++) {
            SysLib.short2bytes(indexPtr, block, offset);
            offset += 2;
        }
        SysLib.rawwrite(indexBlockNumber, block);
        return true;
    }

    /**
     *  Return the block given the offset
     */
    public short findTargetBlock(int offset) {
        int blockNumber = offset/Disk.blockSize;

        if(blockNumber >= directSize) {
            if(this.indirect == -1) {
                return -1;
            }
            // read from the indirect block to gain access to pointers
            byte buffer[] = new byte[Disk.blockSize];
            SysLib.rawread(this.indirect, buffer);
            // traverse the index block
            int offsetLeft = (blockNumber - directSize) * 2;

            return SysLib.bytes2short(buffer, offsetLeft);
        }
        else {
            return this.direct[blockNumber];
        }
    }

    /**
     * Sets the blockNumber to a free direct or indirect pointer
     * and return true if assigned, false indicating out of space
     */
    public boolean setDirectPointers(short blockNumber) {
        if(blockNumber == -1) {  // we ran out of free blocks
            return false;
        }

        // assigning direct blocks
        for(int i = 0; i < directSize; i++) {
            if(this.direct[i] == -1) {
                this.direct[i] = blockNumber;
                return true;
            }
        }

        return false;
    }

    /**
     * Set blockNumber to a free indirect block pointer
     * and return true if assigned, false indicating out of space
     */
    public boolean setIndirectPointers(short blockNumber) {
        int offset = 0;
        // read from the block
        byte buffer[] = new byte[Disk.blockSize];
        SysLib.rawread(this.indirect, buffer);

        for(int i =0; i < 256; i++) {
            // read the offset, convert bytetoshort
            if(SysLib.bytes2short(buffer, offset) ==  -1) {
                SysLib.short2bytes(blockNumber, buffer, offset);
                SysLib.rawwrite(this.indirect, buffer);
                return true;
            }
            offset += 2;
        }
        return false; // no more indirect pointers
    }
}