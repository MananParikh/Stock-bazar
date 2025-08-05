package frontend.src.main.java.frontend;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class Node {
    String key; // stock name
    HashMap<String, Object> value; // key is data/error, value is server's response
    Node next;
    Node prev;

    Node(String key, HashMap<String, Object> value) {
        this.key = key;
        this.value = value;
        this.next = null;
        this.prev = null;
    }
}

public class Cache {
    private int capacity;
    private Map<String, Node> cacheMap;
    private Node head;
    private Node tail;
    private Map<String, ReentrantReadWriteLock> cacheReadWriteLocks = new ConcurrentHashMap<>(); // read locking for each stock (lookups)


    // Constructor to initialize the cache with a given
    // capacity
    Cache(int capacity) {
        this.capacity = capacity;
        this.cacheMap = new HashMap<>();
        HashMap<String, Object> emptyObject = new HashMap<>();
        this.head = new Node("", emptyObject);
        this.tail = new Node("", emptyObject);
        this.head.next = this.tail;
        this.tail.prev = this.head;
    }

    // Function to get the value for a given key
    HashMap<String, Object> get(String key) {
        if (!cacheMap.containsKey(key)) {
            return new HashMap<>();
        }

        Node node = cacheMap.get(key);
        remove(node);
        add(node);
        return node.value;
    }

    // Function to put a key-value pair into the cache
    void put(String key, HashMap<String, Object> value) {
        System.out.println("Adding " + key + " to cache.");
        if (cacheMap.containsKey(key)) {
            System.out.println("For " + key + ", we already had entry in our record, will update it.");
            Node oldNode = cacheMap.get(key);
            remove(oldNode);
        }

        Node node = new Node(key, value);

        if (cacheMap.size() > capacity) {
            Node nodeToDelete = tail.prev;
            System.out.println("Cache capacity exceeded. Removing entry for " + nodeToDelete.key);
            remove(nodeToDelete);
            cacheMap.remove(nodeToDelete.key);
        }
        cacheMap.put(key, node);
        add(node);

        System.out.println("Moved " + key + " to the head of the cache.");
    }

    // Add a node right after the head (most recently used
    // position)
    private void add(Node node) {
        Node nextNode = head.next;
        head.next = node;
        node.prev = head;
        node.next = nextNode;
        nextNode.prev = node;
    }

    // Remove a node from the list
    private void remove(Node node) {
        Node prevNode = node.prev;
        Node nextNode = node.next;
        prevNode.next = nextNode;
        nextNode.prev = prevNode;
    }

    // Removes a specific entry from the cache based on the key
    public void removeEntry(String key) {
        if (cacheMap.containsKey(key)) {
            cacheReadWriteLocks.putIfAbsent(key, new ReentrantReadWriteLock()); // Give stock a lock
            ReentrantReadWriteLock lock = cacheReadWriteLocks.get(key); // lock corresponds to specific stock
            lock.writeLock().lock(); // Read lock only this specific stock

            try {
                Node node = cacheMap.get(key);
                remove(node);               // Remove from the doubly linked list
                cacheMap.remove(key);       // Remove from the hashmap
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

}
