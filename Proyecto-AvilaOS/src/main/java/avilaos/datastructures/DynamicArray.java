/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.datastructures;

/**
 * Arreglo dinamico propio. Crece solo cuando se llena y permite borrar en cualquier
 * posicion (lo necesita la cola de E/S del disco: SSTF y SCAN sacan cualquier elemento).
 * No es seguro entre hilos: quien lo use lo protege con su semaforo.
 * @param <T> tipo de los elementos
 */
public class DynamicArray<T> {
    private static final int INITIAL_CAPACITY = 8;

    private Object[] items = new Object[INITIAL_CAPACITY];
    private int size;

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    public void add(T item) {
        if (size == items.length) {
            grow();
        }
        items[size++] = item;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        checkIndex(index);
        return (T) items[index];
    }

    public void set(int index, T item) {
        checkIndex(index);
        items[index] = item;
    }

    /** Borra el elemento de esa posicion, corre los demas y lo devuelve. */
    @SuppressWarnings("unchecked")
    public T removeAt(int index) {
        checkIndex(index);
        T removed = (T) items[index];
        for (int i = index; i < size - 1; i++) {
            items[i] = items[i + 1];
        }
        items[--size] = null;
        return removed;
    }

    /** Borra la primera aparicion del elemento. Devuelve false si no estaba. */
    public boolean remove(T item) {
        int index = indexOf(item);
        if (index < 0) {
            return false;
        }
        removeAt(index);
        return true;
    }

    /** Posicion de la primera aparicion (por equals) o -1. */
    public int indexOf(T item) {
        for (int i = 0; i < size; i++) {
            if (item == null ? items[i] == null : item.equals(items[i])) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(T item) {
        return indexOf(item) >= 0;
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            items[i] = null;
        }
        size = 0;
    }

    /** Copia independiente (los elementos se comparten, el arreglo no). */
    public DynamicArray<T> copy() {
        DynamicArray<T> other = new DynamicArray<>();
        for (int i = 0; i < size; i++) {
            other.add(get(i));
        }
        return other;
    }

    private void grow() {
        Object[] bigger = new Object[items.length * 2];
        for (int i = 0; i < size; i++) {
            bigger[i] = items[i];
        }
        items = bigger;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Posicion " + index + " fuera de rango (tamano " + size + ")");
        }
    }
}
