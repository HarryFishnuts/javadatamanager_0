/* <Mem.java>
 * Bailey Jia-Tao Brown
 * 2022
 * 
 * DESCRIPTION:
 *  The goal of this class is to provide storage/caching of references
 *  to already initialized objects so that they may be reused as
 *  opposed to being discarded for the GC to consume. This library is
 *  meant to mimic the C-style of allocating and freeing memory.
 *  
 *  An ideal use of this class is within a large loop which frequently
 *  allocates memory to the heap. Instead of discarding the allocated
 *  data once the variable scope is over, it's reference will be saved
 *  to the "backend" of this class, and may be reused on the next loop
 *  iteration.
 * 
 * CONTENTS:
 *  - Imports
 *  - Class block
 *  - Private class macros
 *  - Internal "Page" class definition
 *      - Page logging flags
 *      - Page logging function
 *      - Page info members
 *      - Page buffer members
 *      - Page cache member
 *      - Page constructor
 *      - Page info retrival function
 *      - Page allocate function
 *      - Internal "PCache" class definition
 *          - Cache info members
 *          - Cache buffer members
 *          - Cache constructor
 *          - Cache allocate function
 *          - Cache free function
 *      - Page free function
 *      - Page forcefree function
 *      - Page cache dump function
 *      - Page buffer dump function
 *  - Info members
 *  - Buffer members
 *  - Cache members
 *  - Cache allocate function
 *  - Alloc function
 *  - Free function
 *  - Dump function
 *  - Pagedump function
 *  - Cachedump function (L2)
 *  - Cachedump function (L1)
 */

 /* ===== IMPORTS ===== */
 import java.util.*;
 import java.lang.reflect.*;
 import javax.swing.*;
import javax.swing.text.DefaultEditorKit.InsertBreakAction;

 /* ===== CLASS BLOCK ===== */
 public final class Mem
 {
    /* ===== PAGE LOGGING FLAGS ===== */
    public static boolean debug_log = false;

    /* ===== PAGE LOGGING FUNCTION ===== */
    private static void pageLog(String format, Object... args)
    {
        if (debug_log) 
        {
            System.out.printf(format, args);
        }
    }

    /* ===== CLASS MACROS ===== */
    private static final int PAGESIZE = 0x200;
    private static final int DIVERSITY = 0x1f;
    private static final int UNUSED = 0b01000000; /* one off from sign */
    private static final int L1CACHESIZE = 0x10;
    private static final int L2CACHESIZE = 0x20;
    private static final int CACHEEMPTY = -1;
    private static final int PAGECOUNT = 0x100;

    /* ===== PAGE CLASS ===== */
    private static class Page
    {
        /* ===== PAGE INFO MEMBERS ===== */
        public short info_diversity = 0;
        public short info_bufferuse = 0;
        
        /* ===== PAGE BUFFER MEMBERS ===== */
        private Class[] buffer_divMap; /* index map to each class */
        private byte[] buffer_divField; /* class index field */
        private Object[] buffer_data; /* object data buffer */

        /* ===== PAGE CACHE MEMBER ===== */
        private PCache cache;

        /* ===== PAGE CONSTRUCTOR ===== */
        public Page()
        {
            /* heap allocate buffers */
            buffer_divMap = new Class[DIVERSITY];
            buffer_divField = new byte[PAGESIZE];
            buffer_data = new Object[PAGESIZE];

            /* set all divIndex values to unused */
            for (int i = 0; i < PAGESIZE; i++)
            {
                buffer_divField[i] |= UNUSED;
            }

            /* init cache member */
            cache = new PCache();
        } /* PAGE CTOR END */

        /* ===== PAGE INFO RETRIVAL FUNCTION ===== */
        public int getPageInfo()
        {
            return info_diversity | (info_bufferuse << 16);
        } /* PAGE INFO END */

        /* ===== PAGE ALLOCATE FUNCTION ===== */
        /* returns null on failure */
        public Object alloc(Class type, int page)
        {
            pageLog("Pagealloc called\n");

            /* first, register class to diversity Map */
            /* search for collision, if none, add class */
            int typeIndex = 0;
            for (int i = 0; i < DIVERSITY; i++)
            {
                /* on collide */
                if (type.equals(buffer_divMap[i]))
                {
                    pageLog("Div collision at divMap[%d]\n", i);
                    typeIndex = i;
                    break;
                }

                /* on free */
                if (buffer_divMap[i] == null)
                {
                    typeIndex = i;
                    buffer_divMap[i] = type;
                    info_diversity++; /* increment div count */
                    pageLog("Created div at divMap[%d]\n", i);
                    break;
                }
            } /* BUFFER SEARCH LOOP END */

            /* variable for storing free data index for object to alloc */
            int allocIndex = 0;
            
            /* search for free data buffer spot */
            /* if data buffer is full, return null */
            if (info_bufferuse >= PAGESIZE) return null;

            /* start search between total buffer use and start */
            int startIndex = info_bufferuse / 2;
            for (int i = startIndex; i < PAGESIZE; i++)
            {
                /* on used, continue */
                if ((buffer_divField[i] & UNUSED) == 0) continue;

                /* NOW, ALL ARE UNUSED */
                /* on unused, check for not matching */
                if (typeIndex != (buffer_divField[i] & ~UNUSED))
                {
                    /* NOW, ALL ARE UNUSED & UNMATCHING */
                    /* if unmatching and not null, continue */
                    if (buffer_data[i] != null) continue;
                } 

                /* NOW, ALL ARE UNUSED, AND COULD BE NULL */
                /* on unused and matching, check if not null */
                if (buffer_data[i] != null)
                {
                    /* assign instance to buffer index  and change divField */
                    buffer_data[i] = buffer_data[i];
                    buffer_divField[i] = (byte)typeIndex;

                    /* add object to L1 cache */
                    cacheAlloc(buffer_data[i].hashCode(),
                     page, i);

                    /* add object to L2 cache */
                    cache.alloc(buffer_data[i], i);

                    /* return object reference and increment usecount */
                    pageLog("Alloced object at index: %d\n", i);
                    info_bufferuse++;
                    return buffer_data[i];
                }

                /* on unused, try to get class ctor */
                Constructor objCtor;
                try { objCtor = type.getConstructor(null); }
                catch (Exception exception) { pageLog("Alloc failed: Faulty ctor\n");
                    return null; }

                /* afterwards, try to create instance */
                Object instance;
                try { instance = objCtor.newInstance(null); }
                catch (Exception exception) { pageLog("Alloc failed: Faulty init\n");
                    return null; }

                /* assign instance to buffer index  and change divField */
                buffer_data[i] = instance;
                buffer_divField[i] = (byte)typeIndex;

                /* add to L1 cache */
                cacheAlloc(instance.hashCode(), page, i);

                /* add object to cache */
                cache.alloc(instance, i);

                /* return object reference and increment usecount */
                pageLog("Alloced object at index: %d\n", i);
                info_bufferuse++;
                return instance;
            } /* DATA BUFFER SEARCH END */

            /* on buffer search exit, failure */
            pageLog("Alloc failed: Buffer search exit\n");
            return null;
        } /* PAGE ALLOC FUNCTION END */

        /* ===== PCACHE CLASS ===== */
        public final class PCache
        {
            /* ===== PCACHE INFO MEMBERS ===== */
            public long info_cacheAge = 0;
            
            /* ===== PCACHE BUFFER MEMBERS ===== */
            public int[] buffer_hashdata; /* hash data buffer  */
            public long[] buffer_ageField; /* age of each hash data index */
            public int[] buffer_indexMap; /* page index of each data index */

            /* ===== CACHE CONSTRUCTOR ===== */
            public PCache()
            {
                /* heap alloc buffers */
                buffer_hashdata = new int[L2CACHESIZE];
                buffer_ageField = new long[L2CACHESIZE];
                buffer_indexMap = new int[L2CACHESIZE];

                /* set all to empty */
                for (int i = 0; i < L2CACHESIZE; i++)
                {
                    buffer_hashdata[i] = CACHEEMPTY;
                }
            }

            /* ===== CACHE ALLOCATE FUNCTION ===== */
            public void alloc(Object obj, int index)
            {
                /* increment cache age */
                info_cacheAge++;

                /* increment buffer age */
                for (int i = 0; i < L2CACHESIZE; i++)
                {
                    if (buffer_hashdata[i] == CACHEEMPTY) continue;
                    buffer_ageField[i]++;
                }

                /* get hash code */
                int objHash = obj.hashCode();

                /* search for oldest cache entry */
                long oldest = 0;
                int oldestIndex = 0;
                for (int i = 0; i < L2CACHESIZE; i++)
                {
                    /* on empty, assign and return */
                    if (buffer_hashdata[i] == CACHEEMPTY)
                    {
                        /* assign buffer data */
                        buffer_hashdata[i] = objHash;
                        buffer_ageField[i] = 0;
                        buffer_indexMap[i] = index;
                        pageLog("Empty cache spot at: %d\n",
                            i);
                        return;
                    }

                    /* update oldest & oldest index */
                    if (oldest < buffer_ageField[i])
                    {
                        oldest = buffer_ageField[i];
                        oldestIndex = i;
                    }
                } /* GET OLDEST LOOP END */

                /* assign oldest index to new data */
                pageLog("Replaced oldest cache: %d\n", oldestIndex);
                buffer_hashdata[oldestIndex] = objHash;
                buffer_ageField[oldestIndex] = 0;
                buffer_indexMap[oldestIndex] = index;
                return;
            } /* END PCACHE ALLOC FUNCTION */

            /* ===== PCACHE FREE FUNCTION ===== */
            /* returns page index of freed object, -1 for not found */
            public int free(int hashCode)
            {
                /* search hashbuffer for match */
                for (int i = 0; i < L2CACHESIZE; i++)
                {
                    /* on match, free hash and return page index */
                    if (hashCode == buffer_hashdata[i])
                    {
                        buffer_hashdata[i] = CACHEEMPTY;
                        return buffer_indexMap[i];
                    }
                }

                /* failure */
                return -1;
            }
        } /* END PCACHE BLOCK */

        /* ===== PAGE FREE FUNCTION ===== */
        /* returns 1 on sucess, 0 on failure */
        public int free(Object toFree)
        {
            pageLog("Pagefree called\n");
            /* get object hash code */
            int hashCode = toFree.hashCode();

            /* try freeing from cache */
            int freeIndex = cache.free(hashCode);

            /* on cachefault, brute force it */
            if (freeIndex == -1)
            {
                pageLog("Cachefault!\n");

                /* loop through buffer */
                for (int i = 0; i < PAGESIZE; i++)
                {
                    /* ignore if unused or if null */
                    if ((buffer_divField[i] & UNUSED) == 1) continue;
                    if (buffer_data[i] == null) continue;
                    /* compare hashcodes */
                    if (buffer_data[i].hashCode() == hashCode)
                    {
                        /* set divField to unused and return */
                        pageLog("Freed object at index: %d\n", i);
                        buffer_divField[i] |= UNUSED;
                        info_bufferuse--;
                        return 1;
                    } /* HASH COLLIDE CHECK END */
                } /* BUFFER LOOP END */
                /* on loop exit, fail */
                pageLog("Free failed: Buffer search exit\n");
                return 0;
            } /* CACHEFAULT CONDITION END */
            else
            {
                buffer_divField[freeIndex] |= UNUSED;
                pageLog("Freed object at index: %d\n", freeIndex);
                info_bufferuse--;
                return 1;
            } /* CACHE HIT CONDITION END */
        } /* PAGE FREE FUNCTION END */

        /* ===== PAGE FORCE FREE FUNCTION ===== */
        /* 1 on sucess, 0 on fail */
        public int forceFree(int index)
        {
            /* on already unused */
            if ((buffer_divField[index] & UNUSED) != 0)
            {
                pageLog("Forcefree failed! Object already free\n");
                return 0;
            }

            buffer_divField[index] |= UNUSED;
            pageLog("Forcefreed object at index: %d\n", index);
            info_bufferuse--;
            return 1;
        }

        /* ===== PAGE CACHE DUMP FUNCTION ===== */
        public void cacheDump()
        {
            System.out.printf("PAGE CACHE CONTENTS:\n");
            for (int i = 0; i < L2CACHESIZE; i++)
            {
                System.out.printf("\tIndex: [%02d]\n", i);
                System.out.printf("\t\tHashCode: <%012d>\n",
                    cache.buffer_hashdata[i]);
                System.out.printf("\t\tEntryAge: <%012d>\n",
                    cache.buffer_ageField[i]);
                System.out.printf("\t\tPageIndex: <%012d>\n",
                    cache.buffer_indexMap[i]);
            }
        } /* PAGE CACHE DUMP FUNCTION END */

        /* ===== PAGE BUFFER DUMP FUNCTION ===== */
        public void bufferDump()
        {
            System.out.printf("PAGE BUFFER CONTENTS:\n");
            for (int i = 0; i < PAGESIZE; i++)
            {
                /* on empty, continue */
                if ((buffer_divField[i] & (~UNUSED)) == 1) continue;
                if (buffer_data[i] == null) continue;
                System.out.printf("\tIndex: [%03d]\n", i);
                System.out.printf("\t\tObject Hash: %012d\n",
                    buffer_data[i].hashCode());
                System.out.printf("\t\tObject Type: %d\n",
                    buffer_divField[i] & ~UNUSED);
            }
        } /* PAGE BUFFER DUMP END */
    } /* PAGE CLASS BLOCK END */

    /* ===== INFO MEMBERS ===== */
    public static int info_pageCount = 0;

    /* ===== BUFFER MEMBERS ===== */
    private static Page[] buffer_pages = new Page[PAGECOUNT];

    /* ===== CACHE MEMBERS ===== */
    private static int[] cache_hashdata = new int[L1CACHESIZE];
    private static long[] cache_indexmap = new long[L1CACHESIZE];
    private static long[] cache_agemap = new long[L1CACHESIZE];
    private static byte[] cache_usemap = new byte[L1CACHESIZE];

    /* ===== CACHE ALLOCATE FUNCTION ===== */
    private static void cacheAlloc(int hashData, int page, int index)
    {
        pageLog("L1 CACHEALLOC CALLED\n");

        /* update all ages */
        for (int i = 0; i < L1CACHESIZE; i++)
        {
            cache_agemap[i]++;
        }

        /* loop through buffer and get oldest */
        long oldestval = -1;
        int oldestIndex = -1;
        for (int i = 0; i < L1CACHESIZE; i++)
        {
            /* check if cache is empty */
            if (cache_usemap[i] == 0)
            {
                /* assign data */
                cache_hashdata[i] = hashData;
                cache_agemap[i] = 0;
                cache_usemap[i] = 1;
                cache_indexmap[i] = index | (page << 16);
                pageLog("L1 Alloc at index: %d\n", i);
                return;
            } /* EMPTY CONDITION END */

            /* get new oldest value */
            if (cache_agemap[i] > oldestval)
            {
                oldestval = cache_agemap[i];
                oldestIndex = i;
            } /* OLDEST UPDATE END */
        } /* BUFFER SEARCH LOOP END */
        /* on search exit, assign to oldest index */
        cache_hashdata[oldestIndex] = hashData;
        cache_agemap[oldestIndex] = 0;
        cache_usemap[oldestIndex] = 1;
        cache_indexmap[oldestIndex] = index | (page << 16);
        pageLog("L1 Alloc at index: %d\n", oldestIndex);
        return;
    } /* CACHEALLOC FUNCTION END */

    /* ===== ALLOCATE FUNCTION ===== */
    public static Object alloc(Class type)
    {
        pageLog("ALLOC CALLED\n");

        /* loop through all pages */
        for (int i = 0; i < PAGECOUNT; i++)
        {
            /* if null, create page */
            if (buffer_pages[i] == null)
            {
                buffer_pages[i] = new Page();
                info_pageCount++;
            }
            
            /* if page is full, continue */
            if (buffer_pages[i].info_bufferuse == PAGESIZE) continue;

            /* allocate from page */
            return buffer_pages[i].alloc(type, i);
        }

        /* on exit, failure */
        System.err.printf("OUT OF PAGES!\n");
        return null;
    } /* ALLOC FUNCTION END */

    /* ===== FREE FUNCTION ===== */
    /* returns 1 on sucess, 0 on failure */
    public static int free(Object toFree)
    {
        pageLog("FREE CALLED\n");

        /* first, check L1 cache */
        pageLog("Checking L1 cache\n");
        int hashCode = toFree.hashCode();
        for (int i = 0; i < L1CACHESIZE; i++)
        {
            /* on hit */
            if (cache_hashdata[i] == hashCode)
            {
                /* crazy bit hack */
                long index = cache_indexmap[i] & (long)0xFFFF;
                long page = cache_indexmap[i] >> 16;

                /* forcefree */
                pageLog("L1 hit data: P:%d I:%d\n", page, index);
                int status = buffer_pages[(int)page].forceFree((int)index);
                
                /* check if hit */
                if (status == 1)
                {
                    pageLog("Object hit at L1, freed sucess!\n");
                    return 1;
                }
                else
                {
                    /* on L1 failure, try L2*/
                    pageLog("Free failed: L1 and page mismatch\n");
                    pageLog("Attempting to search L2 cache\n");

                    /* free L1 */
                    cache_usemap[i] = 0;
                    break;
                } /* L1 FREE ATTEMPT END */
            } /* L1 HIT CHECK END */
        } /* L1 BUFFER SEARCH END */

        pageLog("No hit at L1, trying L2...\n");

        /* loop through all pages */
        int faults = 0;
        int inSearch = 0;
        for (int i = 0; i < info_pageCount; i++)
        {
            /* zig zag from last page to first page inwards */
            /* so that last tested page will be roughly in the */
            /* middle */
            int j = 0;
            if (i % 2 == 0)
            {
                j = inSearch;
            }
            else
            {
                j = info_pageCount - inSearch - 1;
                inSearch++;
            }
            
            pageLog("Searching page: %d\n", j);

            /* try to free, if sucess, return */
            int status = buffer_pages[j].free(toFree);
            if (status == 1)
            {
                pageLog("Freed Object Sucessfully!\n");
                pageLog("L2 Cachefaults: %d\n", faults);
                return 1;
            } 
            faults++;
        }   

        /* fail condition */
        System.err.printf("COULD NOT FREE OBJECT\n");
        return 0;
    } /* FREE FUNCTION END */

    /* ===== DUMP FUNCTION ===== */
    public static void dump()
    {
        /* loop through all pages */
        for (int i = 0; i < info_pageCount; i++)
        {
            System.out.printf("=====DUMPING PAGE: <%d>=====\n", i);
            buffer_pages[i].cacheDump();
            buffer_pages[i].bufferDump();
        }
    }

    /* ===== PAGEDUMP FUNCTION ===== */
    public static void pageDump(int page)
    {
        if (buffer_pages[page] == null)
        {
            System.err.printf("Could not dump page: Page does not exist!\n");
            return;
        }
        cacheDumpL1();
        buffer_pages[page].cacheDump();
        buffer_pages[page].bufferDump();
    } /* PAGEDUMP FUNCTION END */

    /* ===== CACHEDUMP FUNCTION (L2) ===== */
    public static void cacheDumpL2(int page)
    {
        if (buffer_pages[page] == null)
        {
            System.err.printf("Could not dump page: Page does not exist!\n");
            return;
        }
        buffer_pages[page].cacheDump();
    } /* CACHEDUMP L2 FUNCTION END */

    /* ===== CACHEDUMP FUNCTION (L1) ===== */
    public static void cacheDumpL1()
    {
        System.out.printf("DUMPING L1 CACHE\n");

        for (int i = 0; i < L1CACHESIZE; i++)
        {
            System.out.printf("L1 Index: [%d]\n", i);
            System.out.printf("\tHashval: %02d\n", cache_hashdata[i]);
            System.out.printf("\tAge: %05d\n", cache_agemap[i]);
            System.out.printf("\tIndex: %d\n", cache_indexmap[i]);
            System.out.printf("\tUseage: %d\n", cache_usemap[i]);
        }
    } /* CACHEDUMP L1 FUNCTION END */

 } /* END CLASS BLOCK */