/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.datastructures;

import java.util.Comparator;

/**
 *Cola propia (FIFO) hecha sobre CustomLinkedList
 * @author Antonella Ruiz
 */
public class CustomQueue <T>{
    private final CustomLinkedList<T> list;
    
    public CustomQueue(){list=new CustomLinkedList<>();}
    
    private CustomQueue(CustomLinkedList<T> list){this.list=list;}
    
    //Operaciones
    
    /**
     * mete el item al final de la fila
     * @param item 
     */
    public void enqueue(T item) {
        list.addLast(item);
}

    /**
     * saca del principio y devuelve el elemento (null si no hay)
     * @return 
     */
    public T dequeue() {
        return list.removeFirst();
    }
    /**
     * mira quien esta de primero sin sacarlo
     * @return 
     */
    public T peek() {
        return list.peekFirst();
    }
    
    //operaciones de apoyo
    
    /**
     * la cola de listos de EDF se ordena por deadline y la de Prioridades por prioridad
     * @param item
     * @param comparator 
     */
    public void insertSorted(T item, Comparator<T> comparator) {
        list.insertSorted(item, comparator);
    }

    /**
     * cuando un proceso termina por vencer su deadline, hay que sacarlo de la cola aunque no este primero
     * @param item
     * @return 
     */
    public boolean remove(T item) {
        return list.remove(item);
    }


    /**
     * la interfaz necesita recorrer la cola para dibujarla
     * @param index
     * @return 
     */
    public T get(int index) {
        return list.get(index);
    }
    
    /**
     * La interfaz necesita una copia para dibujarla mientras corre la simulacion
     * @return 
     */
    public CustomQueue<T> copy() {
        return new CustomQueue<>(list.copy());
    }
    
    public boolean contains(T item) {
        return list.contains(item);
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public void clear() {
        list.clear();
    }
    
}
