/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.model.process;
import java.util.concurrent.Semaphore;

/**
 *Genera identificadores unicos para los procesos
 * @author Antonella Ruiz
 */
public class IdGenerator {
    private static final Semaphore MUTEX = new Semaphore (1);
    private static int nextId=1;
    
    private IdGenerator(){}
    
    public static int nextId(){
        MUTEX.acquireUninterruptibly(); /**adquirir permiso del semaforo */
        try{return nextId++;} /** devolvemos e incrementamos el contador*/
        finally {MUTEX.release();} /** liberamos el permiso*/ 
    }
    
    public static void reset(){
        MUTEX.acquireUninterruptibly(); /** adquiere permiso para proteger el contador*/
        try{nextId=1;} /**reinicio de contador al primer id */
        finally{ MUTEX.release();} /** libera el espacio*/
    
    }
    
    
}
