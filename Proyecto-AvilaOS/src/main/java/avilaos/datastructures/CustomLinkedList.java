/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package avilaos.datastructures;

/**
 *
 * @author Antonella Ruiz
 */
public class CustomLinkedList<T> {
    private Node<T> head;
    private Node<T> tail;
    private int size;
    
    public CustomLinkedList() {
        head = null;
        tail = null;
        size = 0;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    
    public void addLast(T item) {
    Node<T> node = new Node<>(item);
    if (isEmpty()) {
        head = node;
        tail = node;
    } else {
        tail.setNext(node);
        tail = node;
    }
    size++;
    }

    public void addFirst(T item) {
        Node<T> node = new Node<>(item);
        if (isEmpty()) {
            head = node;
            tail = node;
        } else {
            node.setNext(head);
            head = node;
        }
        size++;
    }
    
    public T peekFirst() {
    if (isEmpty()) return null;
    return head.getData();
    }

    public T removeFirst() {
        if (isEmpty()) return null;
        T data = head.getData();
        head = head.getNext();
        if (head == null) tail = null;
        size--;
        return data;
    }
    
}
