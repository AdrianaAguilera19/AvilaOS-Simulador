/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.datastructures;
/** interfaz de java para comparar dos objetos*/
import java.util.Comparator; 
import java.util.concurrent.Semaphore;

/**
 *Implementacion de Lista Enlazada Simple
 * @author Antonella Ruiz
 */
public class CustomLinkedList<T> {
    private Node<T> head;
    private Node<T> tail;
    private int size;
    private final Semaphore mutex = new Semaphore(1);

    public CustomLinkedList() {
        head = null;
        tail = null;
        size = 0;
    }

    public int size() {
        mutex.acquireUninterruptibly();
        try {
            return size;
        } finally {
            mutex.release();
        }
    }

    public boolean isEmpty() {
        mutex.acquireUninterruptibly();
        try {
            return size == 0;
        } finally {
            mutex.release();
        }
    }

    /**
     * Agrega un nuevo elemento al final de la lista
     * si lista vacia, el nuevo nodo se convierte en la cabeza y la cola de la estructura
     * @param item elemto a insertar
     */
    public void addLast(T item) {
        Node<T> node = new Node<>(item);
        mutex.acquireUninterruptibly();
        try {
            if (size == 0) {
                head = node;
                tail = node;
            } else {
                tail.setNext(node);
                tail = node;
            }
            size++;
        } finally {
            mutex.release();
        }
    }

    /**
     * Inserta un elemento al comienzo de la lista
     * @param item elemento a insertar
     */
    public void addFirst(T item) {
        Node<T> node = new Node<>(item);
        mutex.acquireUninterruptibly();
        try {
            if (size == 0) {
                head = node;
                tail = node;
            } else {
                node.setNext(head);
                head = node;
            }
            size++;
        } finally {
            mutex.release();
        }
    }

    /**
     * Obtiene el primer elemento de la lista sin eliminarlo
     * @return primer elemento o null en caso de estar vacia
     */
    public T peekFirst() {
        mutex.acquireUninterruptibly();
        try {
            if (size == 0) return null;
            return head.getData();
        } finally {
            mutex.release();
        }
    }

    /**
     * Elimina y devuelve el primer elemento de la lista
     * @return elemento eliminado o null caso de estar vacia
     */
    public T removeFirst() {
        mutex.acquireUninterruptibly();
        try {
            if (size == 0) return null;
            T data = head.getData();
            head = head.getNext();
            if (head == null) tail = null;
            size--;
            return data;
        } finally {
            mutex.release();
        }
    }

    /**
     * Inserta n elemento respetando el orden definido por el comparador
     * @param item elemento a insertar
     * @param comparator criterio utilizado de comparacion
     */
    public void insertSorted(T item, Comparator<T> comparator) {
        Node<T> node = new Node<>(item);
        mutex.acquireUninterruptibly();
        try {
            if (head == null || comparator.compare(item, head.getData()) < 0) {
                node.setNext(head);
                head = node;
                if (tail == null) tail = node;

            } else {
                Node<T> current = head;
                while (current.getNext() != null
                        && comparator.compare(item, current.getNext().getData()) >= 0) {
                    current = current.getNext();
                }
                node.setNext(current.getNext());
                current.setNext(node);
                //Si el nuevo queda al final se actualiza
                if (node.getNext() == null) tail = node;
            }
            size++;
        } finally {
            mutex.release();
        }
    }

    /**
     * Elimina la primera aparicion del elemento indicado (punteros:current y previous) para recorrer
     * @param item elemento a eliminar
     * @return verdadero si el elemento fue encontrado y eliminado, falso caso contrario
     */
    public boolean remove(T item) {
        mutex.acquireUninterruptibly();
        try {
            Node<T> previous = null;
            Node<T> current = head;
            while (current != null) {
                if (current.getData().equals(item)) {
                    if (previous == null) head = current.getNext();
                    else previous.setNext(current.getNext());
                    if (current == tail) tail = previous;
                    size--;
                    return true;
                }
                previous = current;
                current = current.getNext();
            }
            return false;
        } finally {
            mutex.release();
        }
    }

    /**
     * Obtiene el elemento almacenado en posicion especifica
     * @param index posicion del elemento solicitado
     * @return elemento ubicado en la posicion indicada
     */
    public T get(int index) {
        mutex.acquireUninterruptibly();
        try {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("Indice " + index + ", tamano " + size);
            }
            Node<T> current = head;
            for (int i = 0; i < index; i++) current = current.getNext();
            return current.getData();
        } finally {
            mutex.release();
        }
    }

    /**
     * Verifica si un elemento se encuentra almacenado en la lista
     * @param item elemento a buscar
     * @return true si el elemento existe en la lista, false caso contrario
     */
    public boolean contains(T item) {
        mutex.acquireUninterruptibly();
        try {
            Node<T> current = head;
            while (current != null) {
                if (current.getData().equals(item)) return true;
                current = current.getNext();
            }
            return false;
        } finally {
            mutex.release();
        }
    }

    /**
     * Elimina todos los elementos de la lista
     */
    public void clear() {
        mutex.acquireUninterruptibly();
        try {
            head = null;
            tail = null;
            size = 0;
        } finally {
            mutex.release();
        }
    }

    /**
     * Devuelve una copia independiente (la interfaz la usara para dibujar las colas sin tocar la lista real)
     * @return copia de la lista actual
     */
    public CustomLinkedList<T> copy() {
        CustomLinkedList<T> result = new CustomLinkedList<>();
        mutex.acquireUninterruptibly();
        try {
            Node<T> current = head;
            while (current != null) {
                result.addLast(current.getData());
                current = current.getNext();
            }
        } finally {
            mutex.release();
        }
        return result;
    }

}