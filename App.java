/* <App.java>
 * Bailey Jia-Tao Brown
 * 2022
 * 
 * DESCRIPTION:
 *  Testing class for Mem class
 * 
*/

public class App
{
    /* MAIN */
    public static void main(String[] args)
    {
        String s = (String)Mem.alloc(String.class);
        
        for (int i = 0; i < 8000; i++)
        {
            Vect b = (Vect)Mem.alloc(Vect.class);
            b.y = 10;
        }

        Object obj = Mem.alloc(Object.class);

        for (int i = 0; i < 8000; i++)
        {
            Vect b = (Vect)Mem.alloc(Vect.class);
            b.x = 10;
        }

       // Mem.debug_log = true;
       long t1 = System.nanoTime();
       long m1 = System.currentTimeMillis();
       Mem.free(obj);
       long t2 = System.nanoTime();
       long m2 = System.currentTimeMillis();
       System.out.printf("Free time: <%d>ns\n", t2 - t1);
       System.out.printf("Free time: <%d>ms\n", m2 - m1);

    }
} /* CLASS END */