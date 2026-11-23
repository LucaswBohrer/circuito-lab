package com.circuitolab.model;

/** Um nó do circuito (ponto de ligação). O nó 0 é sempre o terra. */
public class Node {
    public static final int GROUND_ID = 0;

    private final int id;
    private int x;
    private int y;

    public Node(int id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public int getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public boolean isGround() { return id == GROUND_ID; }

    @Override
    public String toString() { return "Nó " + id; }
}
