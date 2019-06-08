/**
 * Created by Phuc (Billy) Huynh on 5/31/2019
 */

public class SuperBlock {

    private final static int DEFAULT_INODE_BLOCKS = 64;
    private final static short END_OF_LIST = -1;

    public int totalBlocks; // the number of disk blocks available on disk
    public int totalInodes; // the number of inodes
    public int freeList; 	// the block number of the free list's head

    /**
     * Constructor
     * sets the disk info as a whole If
     * current formatting does not match, reformat
     */
    public SuperbBlock(int diskSize) {
        // get the per block size from disk (which is 512)
        byte superBlock[] = new byte[Disk.blockSize];
        // read block 0 from disk, and store it into superBlock buff
        SysLib.rawread(0, superBlock);
        // convert the contents in superBlock byte buffer, into ints
        this.totalBlocks = SysLib.bytes2int(superBlock, 0);
        this.totalInodes = SysLib.bytes2int(superBlock, 4);
        this.freeList = SysLib.bytes2int(superBlock, 8);
        if(this.totalBlocks == diskSize && this.totalInodes > 0 && freeList >=2) {
            return;   // disk contents are valid
        }
        else {
            // need to format disk
            this.totalBlocks = diskSize;
            format(DEFAULT_INODE_BLOCKS);
        }
    }

    /**
     * Reformat the disk by setting totalInodes and freelist
     * Intializes each inode blocks to an instance of Inode
     * Intializes each free block with 512 byte array
     * and points to next free block
     */
    public void format(int inodeBlocks) {
        this.totalInodes = inodeBlocks;

        // free list to generalized to any
        if(inodeBlocks % 16 != 0) {
            this.freeList = (inodeBlocks/16) + 2;
        }
        else {
            this.freeList = inodeBlocks/16 + 1;
        }
        // intializing an inode object for each inodeblock
        for(short j =0; j < this.totalInodes; j++) {
            Inode inode = new Inode();
            inode.flag = 0;  // SET IT TO UNUSED
            inode.toDisk(j);
        }
        // linking each free block to each other
        for(int i = this.freeList; i < this.totalBlocks; i++) {
            byte buffer[] = new byte[Disk.blockSize];
            // convert my int to bytes
            SysLib.int2bytes(i+1, buffer, 0);
            SysLib.rawwrite(i, buffer);
        }
        // mark end of freeList marker
        byte buffer[] = new byte[Disk.blockSize];
        SysLib.int2bytes(END_OF_LIST, buffer, 0);
        SysLib.rawwrite(this.totalBlocks -1, buffer);
        this.sync();
    }

    /**
     * write back totalBlocks, inodeBlocks, and freeList to disk
     */
    public void sync() {
        byte superBlock[] = new byte[Disk.blockSize];
        // convert and store in buffer using offset
        SysLib.int2bytes(this.totalBlocks, superBlock, 0);
        SysLib.int2bytes(this.totalInodes, superBlock, 4);
        SysLib.int2bytes(this.freeList, superBlock, 8);
        // write to disk
        SysLib.rawwrite(0, superBlock);
    }

    /**
     * dequeue (remove) the top block from the free list
     * return free block number, -1 if no more free blocks
     */
    public int getFreeBlock() {
        if(this.freeList == -1) {
            return -1;  // no more free block
        }
        int freeBlock = this.freeList;
        // read the 4 bytes, that holds the next free block
        byte buffer[] = new byte[Disk.blockSize];
        SysLib.rawread(freeBlock, buffer);
        // convert this read bytes, into ints
        this.freeList = SysLib.bytes2int(buffer, 0);
        return freeBlock;
    }

    /**
     * Enqueue (push) a given block to the free list
     */
    public boolean returnFreeBlock(int blockNumber) {
        //SysLib.cout(blockNumber + "\n");
        if(blockNumber > -1 && blockNumber < 1000) {
            // set blockNumber's pointer to END OF LIST marker
            byte buffer[] = new byte[Disk.blockSize];
            for(int i = 0; i < Disk.blockSize; i++) {
                buffer[i] = 0;  // intialize all blocks to zero
            }
            SysLib.int2bytes(this.freeList, buffer, 0);
            SysLib.rawwrite(blockNumber, buffer);
            this.freeList = blockNumber;
            return true;
        }
        else {
            SysLib.cerr("In Superblock.java in returnFreeBlock(): blockNumber is out of range\n");
        }
        return false;
    }
}
