/**
 *  Created by Phuc (billy) Huynh on 6/1/09
 *  Starting from the blocks after the superblock, will be the inode blocks.
 *  Each inode describes ONE file, and is 32 bytes
 *
 *  It includes 12 pointers of the index block.
 *  The FIRST 11 of these pointers point to DIRECT blocks.
 *  the LAST pointer points to an INDIRECT block
 *  16 inodes can be stored in ONE block
 *  Each inode muse include:
 *  1. the length of the corresponding file
 *  2. the number of file (structure) table entries that point to this inode
 *  3. the flags indicate (self determined)
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
    public short findIndexBlockNumber() {
        return this.indirect;
    }

    /**
     * Formats the indirect block
     * Return false if not all direct poitners are used or
     * if indirect pointer is used else, true
     */
    public boolean registerIndexBlock(short indexBlockNumber) {
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
    public int findTargetBlock(int offset) {
        int var1 = offset / Disk.blockSize;
        if (var1 < 11) {
            return this.direct[var1];
        } else if (this.indirect < 0) {
            return -1;
        } else {
            byte[] var2 = new byte[Disk.blockSize];
            SysLib.rawread(this.indirect, var2);
            int var3 = var1 - 11;
            return SysLib.bytes2short(var2, var3 * 2);
        }
    }

    /**
     * Sets the data in the block at the given blockNumber to the inode found at
     * the given offset.
     * Return 0 if the block is registered successfully,
     */

    public int registerTargetBlock(int offset, short blockNumber)
    {
        int blockPosition = offset / Disk.blockSize;
        if (blockPosition < 11)
        {
            if (this.direct[blockPosition] != -1)
            {
                return -1;
            }
            if (blockPosition > 0 && this.direct[blockPosition - 1] == -1)
            {
                return -2;
            }

            this.direct[blockPosition] = blockNumber;
            return 0;
        }
     else if (this.indirect < 0) {
            return -3;
        } else {
            byte[] block = new byte[Disk.blockSize];
            SysLib.rawread(this.indirect, block);
            int var5 = blockPosition - 11;
            if (SysLib.bytes2short(block, var5 * 2) > 0) {
                SysLib.cerr("indexBlock, indirectNumber = " + var5 + " contents = " + SysLib.bytes2short(block, var5 * 2) + "\n");
                return -1;
            } else {
                SysLib.short2bytes(blockNumber, block, var5 * 2);
                SysLib.rawwrite(this.indirect, block);
                return 0;
            }
        }
    }
    
    /**
     * Frees a block, returning the data within the block and unregistering the
     * block.
     * Return The byte array of data from the block. Returns null if the block
     * is unregistered.
     */
    public byte[] unregisterIndexBlock() {
        if (this.indirect >= 0) {
            byte[] newBlock = new byte[Disk.blockSize];
            SysLib.rawread(this.indirect, newBlock);
            this.indirect = -1;
            return newBlock;
        } else {
            return null;
        }
    }
}
