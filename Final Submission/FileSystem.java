/*
 * File System Class
 * Simulates file system in java ThreadOS
 * Ajeet Dhaliwal, Kaib Cropley, Mr. Billy 
 */

public class FileSystem {
	
	//Variables associated with class
	private FileTable fileT;
	private Directory dir;
    private SuperBlock supBlock;
    
    //Global constants associated with class
    public static final String ROOT = "/";
    public static final String WRITE = "w";
    public static final String READ = "r";
    public static final String APPEND = "a";
    public static final int BYTES = 512;
    public static final String BERROR = "Error in program";
    
    
    //seek constants
    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    
    private final int SEEK_END = 2;

    //initialize file system
    //accords to superblock size input parameter
    //maximum sizes of processes
    public FileSystem(int supSize) {
        initObj(supSize);
        //gets f entry according to this root directory /
        FileTableEntry fEntry = this.open(ROOT, READ);
        //get the file size in the file entry
        int byteSize = this.fsize(fEntry);
        if (byteSize > 0) {
        	//place to store data fetched
            byte[] data = new byte[byteSize];
            //read data into this data store
            this.read(fEntry, data);
            //reflect changes in directory
            this.dir.bytes2directory(data);
        }
        
        
        this.close(fEntry);
    }
    
    
    //initializes objects
    private void initObj(int fEntry) {
    	this.supBlock = new SuperBlock(fEntry);
        this.dir = new Directory(this.supBlock.inodeBlocks);
        this.fileT = new FileTable(this.dir);
    }

    void sync() {
    	//opens root for writing
        FileTableEntry openFile = this.open(ROOT, WRITE);
        //gets this directory in byte form array
        byte[] toBytes = this.dir.directory2bytes();
        
        //allows superblock to sync this new data
        this.write(openFile, toBytes);
        this.close(openFile);
        this.supBlock.sync();
    }
    
    //does the file table have any entries
    private boolean isEmpty() {
    	return this.fileT.fempty();
    }

    
    boolean format(int iNodes) {
        while(!isEmpty()) {
        	//do nothing
        }
        
        //superblock reflects inodes changes
        this.supBlock.format(iNodes);
        this.dir = new Directory(this.supBlock.inodeBlocks);
        this.fileT = new FileTable(this.dir);
        return !false;
    }

    //returns file table entry in given mode if write mode then return null
    FileTableEntry open(String fileName, String mode) {
    	//opens file for given mode in open
        FileTableEntry entry = this.fileT.falloc(fileName, mode);
        if(mode == WRITE && !this.deallocAllBlocks(entry)) {
        	return null;
        } else {
        	return entry;
        }
        
    }

    //closes given file table entry
    boolean close(FileTableEntry fEntry) {
        synchronized(fEntry) {
        	//decrement count
            --fEntry.count;
            if (fEntry.count > 0) {
            	//return true if the count is already less than zero
                return true;
            }
        }

        return this.fileT.ffree(fEntry);
    }

    int fsize(FileTableEntry fEntry) {
        synchronized(fEntry) {
        	//how many inodes in the fEntry
            return fEntry.inode.length;
        }
    }

    //reads data from entry into buffer
    int read(FileTableEntry fEntry, byte[] buffer) {
    	//assume correct prieveleges
        if (fEntry.mode != APPEND && fEntry.mode != WRITE) {
            int destPos = 0;
            int bufferLen = buffer.length; 
            synchronized(fEntry) {
            	//ensure buffer can store information and the entry points to the proper thing
                while(bufferLen > 0 && fEntry.seekPtr < this.fsize(fEntry)) {
                	//target block found
                    int targetBlock = fEntry.inode.findTargetBlock(fEntry.seekPtr);
                    if (targetBlock == -1) {
                        break;
                    }
                    
                    //data to be read into
                    byte[] freshArr = new byte[512];
                    //syslib system call
                    SysLib.rawread(targetBlock, freshArr);
                    //find the start location to read from FileTableEntry
                    int startPos = fEntry.seekPtr % 512;
                    //take mode, offset
                    int offset = BYTES - startPos;
                    //calculate difference
                    int difference = this.fsize(fEntry) - fEntry.seekPtr;
                    //find minimum values
                    int len = Math.min(Math.min(offset, bufferLen), difference);
                    //copy read portion
                    System.arraycopy(freshArr, startPos, buffer, destPos, len);
                    //update seek ptr LOL
                    fEntry.seekPtr += len;
                    //update read locs
                    destPos += len;
                    bufferLen -= len;
                }

                return destPos;
            } //end SYNCRONIZED
        } else {
            return -1;
        }
    }
    
    //uses buffer to write into specfic filetable entry 
    
    int write(FileTableEntry fEntry, byte[] buffer) {
    	//
        if (fEntry.mode == READ) {
            return -1;
        } else {
            synchronized(fEntry) {
                int srcPos = 0;
                int bufferLen = buffer.length;
                
                //check for appropriate buffer
                while(bufferLen > 0) {
                	//find the target block from the seek ptr
                    int tgtBlock = fEntry.inode.findTargetBlock(fEntry.seekPtr);
                    if (tgtBlock == -1) {
                    	//not found
                        short freeBlk = (short)this.supBlock.getFreeBlock();
                        switch(fEntry.inode.registerTargetBlock(fEntry.seekPtr, freeBlk)) {
                        case -3:
                        	//find free node
                            short nextFree = (short)this.supBlock.getFreeBlock();
                            if (!fEntry.inode.registerIndexBlock(nextFree)) {
                                SysLib.cerr(BERROR);
                                return -1;
                            }

                            if (fEntry.inode.registerTargetBlock(fEntry.seekPtr, freeBlk) != 0) {
                                SysLib.cerr(BERROR);
                                return -1;
                            }
                        case 0:
                        default:
                            tgtBlock = freeBlk;
                            break;
                        case -2:
                        case -1:
                            SysLib.cerr(BERROR);
                            return -1;
                        }
                    }
                    //FRESH ARRAY TO read TO
                    byte[] freshArr = new byte[512];
                    if (SysLib.rawread(tgtBlock, freshArr) == -1) {
                        System.exit(2);
                    }
                    
                    //find loc
                    int loc = fEntry.seekPtr % 512;
                    //find the frame offset
                    int offset = 512 - loc;
                    //minimum length field
                    int minLen = Math.min(offset, bufferLen);
                    //array copy call
                    System.arraycopy(buffer, srcPos, freshArr, loc, minLen);
                    //write the target block to fresh array 
                    SysLib.rawwrite(tgtBlock, freshArr);
                    //update seekPtr
                    fEntry.seekPtr += minLen;
                    srcPos += minLen;
                    bufferLen -= minLen;
                    //restart seekPtr if overboard
                    if (fEntry.seekPtr > fEntry.inode.length) {
                        fEntry.inode.length = fEntry.seekPtr;
                    }
                } //END WHILE
                
                //assign inode to Disk field
                fEntry.inode.toDisk(fEntry.iNumber);
                return srcPos;
            }
        }
    }

    //given FileTableEntry, de3allocate its blocks
    private boolean deallocAllBlocks(FileTableEntry fEntry) {
        if (fEntry.inode.count != 1) {
            return false;
        } 
        
        //get index block
        byte[] indexBlk = fEntry.inode.unregisterIndexBlock();
        //check null input
        if (indexBlk != null) {
        	byte index = 0;
        	//to chost
        	short toShrt;
        	//while convering bytes works
        	while((toShrt = SysLib.bytes2short(indexBlk, index)) != -1) {
        		//convert back into super block
        		this.supBlock.returnBlock(toShrt);
                }
        }

        int var5 = 0;

        while(true) {
        	Inode var6 = fEntry.inode;
        	if (var5 >= 11) {
        		
        		fEntry.inode.toDisk(fEntry.iNumber);
        		return !false;
        	}
        	if (fEntry.inode.direct[var5] != -1) {
        		
        		this.supBlock.returnBlock(fEntry.inode.direct[var5]);
        		
        		
        		
        		fEntry.inode.direct[var5] = -1;
        	}

        	++var5;
        }
        
    }

    boolean delete(String fEntry) {
        FileTableEntry newFt = this.open(fEntry, WRITE);
        short iNum = newFt.iNumber;
        return this.close(newFt) && this.dir.ifree(iNum);
    }

    int seek(FileTableEntry fEntry, int offset, int whence) {
        synchronized(fEntry) {
            switch(whence) {
            case SEEK_SET:
                if (offset >= 0 && offset <= this.fsize(fEntry)) {
                    fEntry.seekPtr = offset;
                    break;
                }

                return -1;
            case SEEK_CUR:
                if (fEntry.seekPtr + offset >= 0 && fEntry.seekPtr + offset <= this.fsize(fEntry)) {
                    fEntry.seekPtr += offset;
                    break;
                }

                return -1;
            case SEEK_END:
                if (this.fsize(fEntry) + offset < 0 || this.fsize(fEntry) + offset > this.fsize(fEntry)) {
                    return -1;
                }

                fEntry.seekPtr = this.fsize(fEntry) + offset;
            }

            return fEntry.seekPtr;
        } //end synchronized
    }
}
