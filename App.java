import java.lang.reflect.Constructor;

import javax.swing.event.ChangeEvent;

import java.util.Scanner;
import java.lang.*;

public class App
{
    static final int SECTSIZE = 0x20;
    static final int BUFSIZE = 0x100;
    static Object[][] oBuffer = new Object[SECTSIZE][BUFSIZE];
    static int[][] oField = new int[SECTSIZE][BUFSIZE];
    static Class[] sectMap = new Class[SECTSIZE];

    /* RETURNS OBJECT SECT INDEX, -1 ON FAIL */
    public static int getSect(Class sectType)
    {
        /* generate sect handle */
        System.out.printf("Looking for sect: %s\n", 
            sectType.getCanonicalName());

        /* search buffer */
        for (int i = 0; i < SECTSIZE; i++)
        {
            /* if uninit, init and return */
            if (sectMap[i] == null)
            {
                System.out.printf("Init new sect: %s at %d\n",
                    sectType.getCanonicalName(), i);
                sectMap[i] = sectType;
                return i;
            } /* INIT CONDITION END */

            /* if collision, return */
            if (sectMap[i].equals(sectType))
            {
                System.out.printf("Sect collision <%s> at %d:\n",
                    sectType.getCanonicalName(), i);
                return i;
            } /* COLLISION CONDITION END */
        } /* BUFFER LOOP END */

        /* fail condition */
        System.err.printf("Failed to alloc/find sect %s\n",
            sectType.getCanonicalName());
        return -1;
    } /* GETSECT END */

    /* RETURNS null ON FAIL */
    public static Object alloc(Class classType)
    {
        /* get buffer sect */
        Constructor objCtor; /* object constructor */

        /* get constructor */
        /* IF CTOR INVALID, DON'T REGISTER SECT */
        try {
            objCtor = classType.getConstructor(null);
        } catch (Exception expt) {
            System.err.printf("%s\n", expt.toString());
            System.err.printf("Could not resolve Ctor!\n");
            return null;
        }
        int sect = getSect(classType);

        /* loop thru object buffer */
        for (int i = 0; i < BUFSIZE; i++)
        {
            /* find empty field spot */
            if (oField[sect][i] == 0)
            {
                /* if buffer uninit, init */
                if (oBuffer[sect][i] == null)
                {
                    System.out.printf("Init Object at oBuf index: %d %d\n",
                        sect, i);

                        /* TRY INIT W/CTOR */
                        try {
                            oBuffer[sect][i] = objCtor.newInstance(null);
                        } catch (Exception err)
                        {
                            System.out.printf("%s\n", err.toString());
                            System.out.printf("Object creation failed!\n");
                            return null;
                        } /* INIT TRY BLOCK END */               
                } /* BUF INIT CHECK END */

                /* set field and return */
                oField[sect][i] = 1;
                System.out.printf("Alloced Object at: %d %d\n", sect, i);
                return oBuffer[sect][i];
            } /* FIELD CHECK END */
        } /* BUFFER LOOP END */

        /* fail condition */
        System.err.printf("Failed to alloc!\n");
        return null;
    }

    /* FREE FUNCTION */
    public static void free(Object toFree)
    {
        Class fClass = toFree.getClass();
        int sect = getSect(fClass);

        /* search buffer for matching object */
        for (int i = 0; i < BUFSIZE; i++)
        {
            /* COLLISION CHECK */
            if (oBuffer[sect][i] == null) continue;
            if (toFree.hashCode() == oBuffer[sect][i].hashCode())
            {
                System.out.printf("Freed obj at: %d %d\n", sect, i);
                oField[sect][i] = 0;
                return;
            }
        } /* BUF LOOP END */
        System.err.printf("Could not find object to free\n");
    }

    /* MEMDUMP function */
    public static void memDump()
    {
        /* SECT LOOP */
        for (int i = 0; i < SECTSIZE; i++)
        {
            /* nullcheck */
            if (sectMap[i] == null) continue;
            System.out.printf("Current sect [%03d]: %s\n", i,
                sectMap[i].getCanonicalName());
            for (int j = 0; j < BUFSIZE; j++)
            {
                if (oBuffer[i][j] == null) continue;
                System.out.printf("\tObject [%03d]: %012d\t State: %d\n",
                    j, oBuffer[i][j].hashCode(), oField[i][j]);
            }
        }
    }
    
    /* MAIN */
    public static void main(String[] args)
    {
        Mem.debug_log = true;
        String s = (String)Mem.alloc(String.class);

        for (int i = 0; i < 50; i++)
        {
            Vect b = (Vect)Mem.alloc(Vect.class);
        }
        Mem.pageSpec();
        
    }
} /* CLASS END */

