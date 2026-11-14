/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.datastructures;

/**
 * Lista enlazada de numeros de bloque, en el orden de la cadena de un archivo.
 * El primero es el bloque inicial; cada bloque "apunta" al siguiente de la lista.
 */
public class BlockChain {

    private static class Link {
        final int block;
        Link next;

        Link(int block) {
            this.block = block;
        }
    }

    private Link head;
    private Link tail;
    private int size;

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    /** Agrega un bloque al final de la cadena. */
    public void add(int block) {
        Link link = new Link(block);
        if (head == null) {
            head = link;
        } else {
            tail.next = link;
        }
        tail = link;
        size++;
    }

    /** Primer bloque de la cadena, o -1 si esta vacia. */
    public int first() {
        return head == null ? -1 : head.block;
    }

    /** Bloque en esa posicion de la cadena, o -1 si no existe. */
    public int get(int index) {
        if (index < 0 || index >= size) {
            return -1;
        }
        Link current = head;
        for (int i = 0; i < index; i++) {
            current = current.next;
        }
        return current.block;
    }

    /** Bloque que sigue a este en la cadena, o -1 si es el ultimo o no esta. */
    public int next(int block) {
        Link current = head;
        while (current != null) {
            if (current.block == block) {
                return current.next == null ? -1 : current.next.block;
            }
            current = current.next;
        }
        return -1;
    }

    public boolean contains(int block) {
        Link current = head;
        while (current != null) {
            if (current.block == block) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    public void clear() {
        head = null;
        tail = null;
        size = 0;
    }

    /** Copia independiente (para snapshots de la GUI). */
    public BlockChain copy() {
        BlockChain other = new BlockChain();
        Link current = head;
        while (current != null) {
            other.add(current.block);
            current = current.next;
        }
        return other;
    }

    /** Los bloques en orden, como arreglo. */
    public int[] toArray() {
        int[] result = new int[size];
        Link current = head;
        int i = 0;
        while (current != null) {
            result[i++] = current.block;
            current = current.next;
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Link current = head;
        while (current != null) {
            sb.append(current.block).append(" -> ");
            current = current.next;
        }
        return sb.append("null").toString();
    }
}